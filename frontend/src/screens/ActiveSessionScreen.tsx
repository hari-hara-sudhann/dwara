import { useCallback, useEffect, useState } from "react";
import { View, Text, ScrollView, Alert, Pressable } from "react-native";
import { useFocusEffect } from "@react-navigation/native";
import { Screen, Card, SectionTitle, PrimaryButton, IconBadge } from "../components/ui";
import { useApp, formatFee } from "../context/AppContext";
import { api } from "../services/mockApi";
import { ble } from "../services/mockBle";
import type { ParkingSession, Vehicle } from "../types";
import type { StackScreenProps } from "../navigation/types";

type GateStep = "idle" | "sending" | "open" | "done";

/** Fixed gate-close timer from firmware D17: gate auto-closes 8s after open. */
const GATE_OPEN_SECONDS = 8;

export function ActiveSessionScreen({ route, navigation }: StackScreenProps<"ActiveSession">) {
  const { activeSession, setActiveSession, refreshActiveSession, vehicles } = useApp();
  const session: ParkingSession | null = activeSession;

  const [now, setNow] = useState(Date.now());
  const [entryStep, setEntryStep] = useState<GateStep>("idle");
  const [exitStep, setExitStep] = useState<GateStep>("idle");
  const [gateCountdown, setGateCountdown] = useState(GATE_OPEN_SECONDS);

  // Re-sync session every time the screen gains focus (e.g. returning from Payment).
  useFocusEffect(
    useCallback(() => {
      void refreshActiveSession();
    }, [refreshActiveSession])
  );

  // Live fee ticker: only while PARKED (backend clock D19 approximated client-side for display).
  useEffect(() => {
    if (session?.status !== "PARKED") return;
    const t = setInterval(() => setNow(Date.now()), 1000);
    return () => clearInterval(t);
  }, [session?.status]);

  // Gate-open countdown after a successful BLE open.
  useEffect(() => {
    if (entryStep !== "open" && exitStep !== "open") return;
    setGateCountdown(GATE_OPEN_SECONDS);
    const t = setInterval(() => {
      setGateCountdown((c) => {
        if (c <= 1) {
          clearInterval(t);
          setEntryStep((s) => (s === "open" ? "idle" : s));
          setExitStep((s) => (s === "open" ? "idle" : s));
          return GATE_OPEN_SECONDS;
        }
        return c - 1;
      });
    }, 1000);
    return () => clearInterval(t);
  }, [entryStep, exitStep]);

  if (!session) {
    return (
      <Screen>
        <SectionTitle title="Session" subtitle="No active session found." />
        <PrimaryButton label="Back to Home" onPress={() => navigation.navigate("Tabs")} />
      </Screen>
    );
  }

  const vehicle: Vehicle | undefined = vehicles.find((v) => v.id === session.vehicleId);
  const elapsedSec =
    session.startedAt && session.status === "PARKED" ? Math.max(0, Math.floor((now - session.startedAt) / 1000)) : 0;

  const openEntryGate = async () => {
    setEntryStep("sending");
    const result = await ble.sendGateCommand("DW001", session.id, "ENTRY");
    if (result === "OPENED") {
      setEntryStep("open");
      const res = await api.confirmEntry(session.id);
      if (res.success) setActiveSession(res.data);
    } else {
      setEntryStep("idle");
      Alert.alert("Gate denied", "The gate refused the entry. Try again near the beacon.");
    }
  };

  const openExitGate = async () => {
    setExitStep("sending");
    const result = await ble.sendGateCommand("DW001", session.id, "EXIT");
    if (result === "OPENED") {
      setExitStep("open");
      const res = await api.confirmExit(session.id);
      if (res.success) setActiveSession(res.data);
    } else {
      setExitStep("idle");
      Alert.alert("Gate denied", "The gate refused the exit. Try again near the beacon.");
    }
  };

  return (
    <ScrollView className="flex-1" showsVerticalScrollIndicator={false}>
      <Screen>
        <SectionTitle title="Your session" subtitle={`Session #${session.id.slice(-6).toUpperCase()}`} />

        {/* Session status card */}
        <Card>
          <View className="flex-row items-center mb-4">
            <IconBadge icon={session.status === "CREATED" ? "log-in" : session.status === "PARKED" ? "car" : "checkmark-done"} />
            <View className="ml-4 flex-1">
              <Text className="text-primary font-bold text-base">{vehicle?.licensePlate ?? "Vehicle"}</Text>
              <Text className="text-muted text-sm mt-0.5">
                {vehicle ? `${vehicle.make} ${vehicle.model}` : ""}
              </Text>
            </View>
            <View className="bg-brand-tint rounded-full px-3 py-1">
              <Text className="text-brand text-xs font-semibold">{session.status}</Text>
            </View>
          </View>

          {session.status === "PARKED" ? (
            <View className="items-center py-2">
              <Text className="text-primary text-2xl font-bold tracking-tight">
                {formatFee((elapsedSec * 0.05))}
              </Text>
              <Text className="text-muted text-sm mt-1">
                {Math.floor(elapsedSec / 60)}m {elapsedSec % 60}s parked · ₹180/hr flat
              </Text>
            </View>
          ) : null}
        </Card>

        {/* STEP 1: entry — only possible while CREATED (wrong intent made impossible by UI). */}
        {session.status === "CREATED" ? (
          <Card>
            <Text className="text-primary font-bold text-base mb-1">Enter the facility</Text>
            <Text className="text-muted text-sm leading-relaxed mb-4">
              Stand near the gate and open the entry gate from here. The gate closes automatically after {GATE_OPEN_SECONDS} seconds.
            </Text>
            {entryStep === "open" ? (
              <View className="bg-brand-tint rounded-btn h-12 items-center justify-center">
                <Text className="text-brand text-base font-semibold">Gate open · closes in {gateCountdown}s</Text>
              </View>
            ) : (
              <PrimaryButton
                label={entryStep === "sending" ? "Talking to gate…" : "Open Entry Gate"}
                onPress={openEntryGate}
                loading={entryStep === "sending"}
              />
            )}
          </Card>
        ) : null}

        {/* STEP 2: parked → go pay */}
        {session.status === "PARKED" ? (
          <Card>
            <Text className="text-primary font-bold text-base mb-1">Leaving?</Text>
            <Text className="text-muted text-sm leading-relaxed mb-4">
              Pay first, then open the exit gate. Payment is mocked — no real money moves.
            </Text>
            <PrimaryButton label="Exit & Pay" onPress={() => navigation.navigate("Payment", { sessionId: session.id })} />
          </Card>
        ) : null}

        {/* STEP 3: paid → exit gate */}
        {session.status === "PAYMENT_PENDING" ? (
          <Card>
            <Text className="text-primary font-bold text-base mb-1">Payment received 🎉</Text>
            <Text className="text-muted text-sm leading-relaxed mb-4">
              {formatFee(session.fee)} paid. Open the exit gate and drive out.
            </Text>
            {exitStep === "open" ? (
              <View className="bg-brand-tint rounded-btn h-12 items-center justify-center">
                <Text className="text-brand text-base font-semibold">Gate open · closes in {gateCountdown}s</Text>
              </View>
            ) : (
              <PrimaryButton
                label={exitStep === "sending" ? "Talking to gate…" : "Open Exit Gate"}
                onPress={openExitGate}
                loading={exitStep === "sending"}
              />
            )}
          </Card>
        ) : null}

        {/* STEP 4: done */}
        {session.status === "EXITED" ? (
          <Card>
            <View className="items-center py-2">
              <IconBadge icon="checkmark-circle" size={32} />
              <Text className="text-primary font-bold text-base mt-4 mb-1">Session complete</Text>
              <Text className="text-muted text-sm leading-relaxed text-center mb-4">
                Final fee {formatFee(session.fee)}. Thanks for parking with Dwara!
              </Text>
              <PrimaryButton
                label="Done"
                onPress={async () => {
                  await refreshActiveSession();
                  navigation.navigate("Tabs");
                }}
              />
            </View>
          </Card>
        ) : null}

        {/* Cancel is only allowed before entry (stale-session abort D20 is the backend's job). */}
        {session.status === "CREATED" ? (
          <Pressable
            onPress={() => {
              Alert.alert("Cancel session", "Abandon this session?", [
                { text: "Keep it", style: "cancel" },
                {
                  text: "Cancel session",
                  style: "destructive",
                  onPress: () => {
                    void api.cancelSession(session.id).then(async () => {
                      await refreshActiveSession();
                      navigation.goBack();
                    });
                  },
                },
              ]);
            }}
            className="h-12 items-center justify-center mb-8"
          >
            <Text className="text-muted text-sm">Cancel session</Text>
          </Pressable>
        ) : null}
      </Screen>
    </ScrollView>
  );
}

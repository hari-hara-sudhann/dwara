import { useCallback, useEffect, useState } from "react";
import { View, Text, ScrollView, Alert, Pressable } from "react-native";
import { useFocusEffect } from "@react-navigation/native";
import { Screen, Card, SectionTitle, PrimaryButton, IconBadge } from "../components/ui";
import { useApp, formatFee } from "../context/AppContext";
import { api } from "../services/api";
import { realBle } from "../services/realBle";
import type { GateOutcome } from "../types";
import type { StackScreenProps } from "../navigation/types";

type GateStep = "idle" | "sending" | "open";

/** Fixed gate-close timer from firmware D17: gate auto-closes 8s after open. */
const GATE_OPEN_SECONDS = 8;

function denyText(outcome: GateOutcome): string {
  switch (outcome.reason) {
    case "PAYMENT_REQUIRED":
      return "Payment is required before the exit gate will open.";
    case "WRONG_STATE_FOR_ENTRY":
      return "This session has already entered the facility.";
    case "STALE_SESSION":
      return "This session is no longer valid. Start a new one.";
    case "WRONG_FACILITY":
      return "This gate belongs to a different facility.";
    case "UNKNOWN_SESSION":
      return "The gate could not find this session. Try again.";
    case "BACKEND_UNREACHABLE":
      return "The gate cannot reach the server. Try again in a moment.";
    case "ACTUATOR_FAULT":
      return "The gate mechanism reported a fault. Ask the attendant.";
    default:
      return `The gate refused the request (${outcome.reason}).`;
  }
}

export function ActiveSessionScreen({ navigation }: StackScreenProps<"ActiveSession">) {
  const { activeSession, setActiveSession, refreshActiveSession, vehicles } = useApp();

  // The backend stops returning a session once it is EXITED, so keep the last
  // known one locally: the completion screen must survive that refresh.
  const [session, setSession] = useState(activeSession);
  const [finished, setFinished] = useState(false);
  const [fee, setFee] = useState<number | null>(null);

  useEffect(() => {
    if (activeSession) setSession(activeSession);
  }, [activeSession]);
  const [entryStep, setEntryStep] = useState<GateStep>("idle");
  const [exitStep, setExitStep] = useState<GateStep>("idle");
  const [gateCountdown, setGateCountdown] = useState(GATE_OPEN_SECONDS);

  // Re-sync from the backend whenever the screen gains focus (payment, gate).
  useFocusEffect(
    useCallback(() => {
      void refreshActiveSession();
    }, [refreshActiveSession])
  );

  // The backend is the billing clock (D19): poll its fee, never compute locally.
  useEffect(() => {
    if (!session || session.sessionStatus !== "PARKED") return;
    let cancelled = false;
    const tick = async () => {
      const res = await api.getFee(session.sessionId);
      if (!cancelled && res.success) setFee(Number(res.data));
    };
    void tick();
    const timer = setInterval(tick, 2000);
    return () => {
      cancelled = true;
      clearInterval(timer);
    };
  }, [session]);

  useEffect(() => {
    if (entryStep !== "open" && exitStep !== "open") return;
    setGateCountdown(GATE_OPEN_SECONDS);
    const timer = setInterval(() => {
      setGateCountdown((current) => {
        if (current <= 1) {
          clearInterval(timer);
          setEntryStep((s) => (s === "open" ? "idle" : s));
          setExitStep((s) => (s === "open" ? "idle" : s));
          return GATE_OPEN_SECONDS;
        }
        return current - 1;
      });
    }, 1000);
    return () => clearInterval(timer);
  }, [entryStep, exitStep]);

  if (!session) {
    return (
      <Screen>
        <SectionTitle title="Session" subtitle="No active session found." />
        <PrimaryButton label="Back to Home" onPress={() => navigation.navigate("Tabs")} />
      </Screen>
    );
  }

  const vehicle = vehicles.find((v) => v.vehicleId === session.vehicleId);
  const shownFee =
    session.totalFee !== null ? Number(session.totalFee) : fee !== null ? fee : Number(session.estimatedFee);

  const runGate = async (intent: "ENTRY" | "EXIT") => {
    const setStep = intent === "ENTRY" ? setEntryStep : setExitStep;
    setStep("sending");
    try {
      const outcome = await realBle.sendGateCommand(
        session.facilityCode,
        session.sessionId,
        intent
      );
      if (outcome.result === "OPENED") {
        setStep("open");
        if (intent === "EXIT") {
          setFinished(true);
        }
        // The firmware has already written the new state in the backend
        // (entry timestamp / exited status); pull it into the UI.
        await refreshActiveSession();
      } else {
        setStep("idle");
        Alert.alert("Gate denied", denyText(outcome));
      }
    } catch (e) {
      setStep("idle");
      Alert.alert("Bluetooth error", (e as Error).message);
    }
  };

  const cancelSession = () => {
    Alert.alert("Cancel session", "Abandon this session?", [
      { text: "Keep it", style: "cancel" },
      {
        text: "Cancel session",
        style: "destructive",
        onPress: async () => {
          const res = await api.cancel(session.sessionId);
          if (!res.success) {
            Alert.alert("Can't cancel", res.message);
            return;
          }
          setActiveSession(null);
          await refreshActiveSession();
          navigation.goBack();
        },
      },
    ]);
  };

  return (
    <ScrollView className="flex-1" showsVerticalScrollIndicator={false}>
      <Screen>
        <SectionTitle
          title="Your session"
          subtitle={`${session.facilityName} · ${session.facilityCode}`}
        />

        <Card>
          <View className="flex-row items-center mb-4">
            <IconBadge
              icon={
                session.sessionStatus === "CREATED"
                  ? "log-in"
                  : session.sessionStatus === "PARKED"
                    ? "car"
                    : "checkmark-done"
              }
            />
            <View className="ml-4 flex-1">
              <Text className="text-primary font-bold text-base">
                {vehicle?.numberPlate ?? session.numberPlate}
              </Text>
              <Text className="text-muted text-sm mt-0.5">{session.facilityName}</Text>
            </View>
            <View className="bg-brand-tint rounded-full px-3 py-1">
              <Text className="text-brand text-xs font-semibold">{session.sessionStatus}</Text>
            </View>
          </View>

          {session.sessionStatus === "PARKED" || session.sessionStatus === "PAYMENT_PENDING" ? (
            <View className="items-center py-2">
              <Text className="text-primary text-2xl font-bold tracking-tight">
                {formatFee(shownFee)}
              </Text>
              <Text className="text-muted text-sm mt-1">
                {session.sessionStatus === "PAYMENT_PENDING"
                  ? "Paid — open the exit gate"
                  : "Charged per second"}
              </Text>
            </View>
          ) : null}
        </Card>

        {session.sessionStatus === "CREATED" ? (
          <Card>
            <Text className="text-primary font-bold text-base mb-1">Enter the facility</Text>
            <Text className="text-muted text-sm leading-relaxed mb-4">
              Stand near the gate and open it from here. The gate closes automatically after{" "}
              {GATE_OPEN_SECONDS} seconds.
            </Text>
            {entryStep === "open" ? (
              <View className="bg-brand-tint rounded-btn h-12 items-center justify-center">
                <Text className="text-brand text-base font-semibold">
                  Gate open · closes in {gateCountdown}s
                </Text>
              </View>
            ) : (
              <PrimaryButton
                label={entryStep === "sending" ? "Talking to gate…" : "Open Entry Gate"}
                onPress={() => void runGate("ENTRY")}
                loading={entryStep === "sending"}
              />
            )}
          </Card>
        ) : null}

        {session.sessionStatus === "PARKED" ? (
          <Card>
            <Text className="text-primary font-bold text-base mb-1">Leaving?</Text>
            <Text className="text-muted text-sm leading-relaxed mb-4">
              Pay first, then open the exit gate. Payment is mocked — no real money moves.
            </Text>
            <PrimaryButton
              label="Exit & Pay"
              onPress={() =>
                navigation.navigate("Payment", { sessionId: session.sessionId })
              }
            />
          </Card>
        ) : null}

        {session.sessionStatus === "PAYMENT_PENDING" ? (
          <Card>
            <Text className="text-primary font-bold text-base mb-1">Payment received</Text>
            <Text className="text-muted text-sm leading-relaxed mb-4">
              {formatFee(shownFee)} paid. Open the exit gate and drive out.
            </Text>
            {exitStep === "open" ? (
              <View className="bg-brand-tint rounded-btn h-12 items-center justify-center">
                <Text className="text-brand text-base font-semibold">
                  Gate open · closes in {gateCountdown}s
                </Text>
              </View>
            ) : (
              <PrimaryButton
                label={exitStep === "sending" ? "Talking to gate…" : "Open Exit Gate"}
                onPress={() => void runGate("EXIT")}
                loading={exitStep === "sending"}
              />
            )}
          </Card>
        ) : null}

        {finished || session.sessionStatus === "EXITED" ? (
          <Card>
            <View className="items-center py-2">
              <IconBadge icon="checkmark-circle" size={32} />
              <Text className="text-primary font-bold text-base mt-4 mb-1">Session complete</Text>
              <Text className="text-muted text-sm leading-relaxed text-center mb-4">
                Final fee {formatFee(shownFee)}. Thanks for parking with Dwara!
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

        {session.sessionStatus === "CREATED" ? (
          <Pressable
            onPress={cancelSession}
            className="h-12 items-center justify-center mb-8"
          >
            <Text className="text-muted text-sm">Cancel session</Text>
          </Pressable>
        ) : null}
      </Screen>
    </ScrollView>
  );
}

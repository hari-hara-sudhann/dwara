import { useRef, useState } from "react";
import { View, Text, ScrollView, Alert, ActivityIndicator } from "react-native";
import { Ionicons } from "@expo/vector-icons";
import { Screen, Card, SectionTitle, PrimaryButton } from "../components/ui";
import { realBle } from "../services/realBle";

type Phase = "idle" | "scanning" | "opening" | "closing";

/**
 * Bench/diagnostic screen: scans for the ESP32 advertising as
 * DWARA-<facilityCode> and drives OPEN / CLOSE straight over BLE.
 *
 * The firmware still validates ENTRY/EXIT against the backend, so a synthetic
 * session id is refused with `UNKNOWN_SESSION` unless the device is flashed
 * with DWARA_REMOTE_AUTH=0 (permissive bench build). CLOSE is local-only.
 */
export function GateTestScreen() {
  const [phase, setPhase] = useState<Phase>("idle");
  const [facilityCode, setFacilityCode] = useState<string | null>(null);
  const [log, setLog] = useState<string[]>([]);
  const logRef = useRef<string[]>([]);

  const appendLog = (line: string) => {
    logRef.current = [`${new Date().toLocaleTimeString()}  ${line}`, ...logRef.current].slice(0, 30);
    setLog(logRef.current);
  };

  const scan = async () => {
    setPhase("scanning");
    setFacilityCode(null);
    appendLog("Scanning for DWARA-* gates…");
    try {
      const beacons = await realBle.scanFacilities(6);
      if (beacons.length === 0) {
        appendLog("No gates found. Move closer and scan again.");
        setFacilityCode(null);
      } else {
        setFacilityCode(beacons[0].facilityCode);
        appendLog(`Found gate: DWARA-${beacons[0].facilityCode}`);
      }
    } catch (e) {
      appendLog(`Scan failed: ${(e as Error).message}`);
    } finally {
      setPhase("idle");
    }
  };

  const sendIntent = async (intent: "ENTRY" | "EXIT" | "CLOSE") => {
    if (!facilityCode) {
      Alert.alert("No gate", "Scan for a gate first.");
      return;
    }
    setPhase(intent === "CLOSE" ? "closing" : "opening");
    appendLog(`Sending ${intent}…`);
    try {
      const sessionId = `test-${Date.now()}`;
      const outcome =
        intent === "CLOSE"
          ? await realBle.sendGateClose(facilityCode, sessionId)
          : await realBle.sendGateCommand(facilityCode, sessionId, intent);
      appendLog(`Result: ${outcome.result} (${outcome.reason})`);
      if (outcome.result === "FAULT") {
        Alert.alert("Gate fault", "The ESP32 reported an actuator fault. Check serial logs.");
      }
    } catch (e) {
      appendLog(`Error: ${(e as Error).message}`);
      Alert.alert("BLE error", (e as Error).message);
    } finally {
      setPhase("idle");
    }
  };

  const busy = phase !== "idle";

  return (
    <ScrollView className="flex-1" showsVerticalScrollIndicator={false}>
      <Screen>
        <SectionTitle
          title="Gate test"
          subtitle="Scan for the ESP32 gate and drive the servo directly over BLE."
        />

        <Card>
          <View className="flex-row items-center mb-4">
            <View className="bg-brand-tint rounded-full p-3 items-center justify-center">
              <Ionicons name="bluetooth" size={24} color="#00B177" />
            </View>
            <View className="ml-4 flex-1">
              <Text className="text-primary font-bold text-base">
                {facilityCode ? `DWARA-${facilityCode}` : "No gate selected"}
              </Text>
              <Text className="text-muted text-sm mt-0.5">
                {phase === "scanning" ? "Scanning…" : "ESP32 · MG90S servo on D27"}
              </Text>
            </View>
            {busy ? <ActivityIndicator color="#00B177" /> : null}
          </View>

          <PrimaryButton
            label={phase === "scanning" ? "Scanning…" : facilityCode ? "Rescan" : "Scan for Gate"}
            onPress={scan}
            loading={phase === "scanning"}
          />
        </Card>

        <SectionTitle title="Controls" />
        <PrimaryButton
          label="Open Gate"
          onPress={() => void sendIntent("ENTRY")}
          disabled={busy || !facilityCode}
          className="mb-4"
        />
        <PrimaryButton
          label="Close Gate"
          onPress={() => void sendIntent("CLOSE")}
          disabled={busy || !facilityCode}
        />

        <View className="h-6" />
        <Card>
          <Text className="text-primary font-bold text-base mb-2">Log</Text>
          {log.length === 0 ? (
            <Text className="text-muted text-sm leading-relaxed">
              Events will appear here after your first scan.
            </Text>
          ) : (
            log.map((line, i) => (
              <Text key={`${i}-${line}`} className="text-muted text-xs leading-relaxed mb-1">
                {line}
              </Text>
            ))
          )}
        </Card>
      </Screen>
    </ScrollView>
  );
}

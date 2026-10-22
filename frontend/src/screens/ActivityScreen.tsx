import { useCallback, useState } from "react";
import { View, Text, ScrollView, ActivityIndicator } from "react-native";
import { useFocusEffect } from "@react-navigation/native";
import { Screen, Card, SectionTitle } from "../components/ui";
import { api } from "../services/api";
import { formatFee } from "../context/AppContext";
import type { ParkingSession } from "../types";

function fmtDate(iso: string | null): string {
  if (!iso) return "—";
  return new Date(iso).toLocaleString([], {
    day: "numeric",
    month: "short",
    hour: "2-digit",
    minute: "2-digit",
  });
}

export function ActivityScreen() {
  const [sessions, setSessions] = useState<ParkingSession[]>([]);
  const [loaded, setLoaded] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useFocusEffect(
    useCallback(() => {
      let cancelled = false;
      (async () => {
        const res = await api.listSessions({ page: 0, size: 50 });
        if (cancelled) return;
        if (res.success) {
          setSessions(res.data.sessions ?? []);
          setError(null);
        } else {
          setError(res.message);
        }
        setLoaded(true);
      })();
      return () => {
        cancelled = true;
      };
    }, [])
  );

  return (
    <ScrollView className="flex-1" showsVerticalScrollIndicator={false} contentContainerClassName="pb-36">
      <Screen>
        <SectionTitle title="Activity" subtitle="Your parking sessions and what they cost." />

        {!loaded ? (
          <Card>
            <View className="py-2 items-center">
              <ActivityIndicator color="#00B177" />
            </View>
          </Card>
        ) : error ? (
          <Card>
            <Text className="text-primary font-bold text-base mb-1">Couldn't load history</Text>
            <Text className="text-muted text-sm leading-relaxed">{error}</Text>
          </Card>
        ) : sessions.length === 0 ? (
          <Card>
            <View className="items-center py-2">
              <Text className="text-primary font-bold text-base mb-1">Nothing yet</Text>
              <Text className="text-muted text-sm leading-relaxed text-center">
                Completed sessions will show up here with their final fee.
              </Text>
            </View>
          </Card>
        ) : (
          sessions.map((s) => (
            <Card key={s.sessionId}>
              <View className="flex-row justify-between items-center mb-2">
                <View className="flex-1 pr-3">
                  <Text className="text-primary font-bold text-base">
                    {s.sessionStatus === "ABORTED"
                      ? "Cancelled"
                      : s.sessionStatus === "EXITED"
                        ? "Completed"
                        : "In progress"}
                  </Text>
                  <Text className="text-muted text-sm mt-0.5">
                    {s.facilityName} · {s.numberPlate}
                  </Text>
                </View>
                <Text className="text-brand font-semibold text-base">
                  {s.totalFee !== null ? formatFee(Number(s.totalFee)) : "—"}
                </Text>
              </View>
              <View className="flex-row justify-between">
                <Text className="text-muted text-sm">In: {fmtDate(s.entryAt)}</Text>
                <Text className="text-muted text-sm">Out: {fmtDate(s.exitAt)}</Text>
              </View>
            </Card>
          ))
        )}
      </Screen>
    </ScrollView>
  );
}

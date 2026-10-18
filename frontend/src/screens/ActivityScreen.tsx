import { useEffect, useState } from "react";
import { View, Text, ScrollView } from "react-native";
import { Screen, Card, SectionTitle } from "../components/ui";
import { api } from "../services/mockApi";
import { formatFee } from "../context/AppContext";
import type { ParkingSession } from "../types";

function fmtDate(ms: number): string {
  return new Date(ms).toLocaleString([], {
    day: "numeric",
    month: "short",
    hour: "2-digit",
    minute: "2-digit",
  });
}

export function ActivityScreen() {
  const [sessions, setSessions] = useState<ParkingSession[]>([]);
  const [loaded, setLoaded] = useState(false);

  useEffect(() => {
    api.listHistory().then((res) => {
      if (res.success) setSessions(res.data);
      setLoaded(true);
    });
  }, []);

  return (
    <ScrollView className="flex-1" showsVerticalScrollIndicator={false} contentContainerClassName="pb-36">
      <Screen>
        <SectionTitle title="Activity" subtitle="Your past parking sessions and what they cost." />

        {!loaded ? (
          <Card>
            <Text className="text-muted text-sm leading-relaxed text-center">Loading…</Text>
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
            <Card key={s.id}>
              <View className="flex-row justify-between items-center mb-2">
                <Text className="text-primary font-bold text-base">
                  {s.status === "ABORTED" ? "Cancelled" : "Completed"}
                </Text>
                <Text className="text-brand font-semibold text-base">
                  {s.fee > 0 ? formatFee(s.fee) : "—"}
                </Text>
              </View>
              <View className="flex-row justify-between">
                <Text className="text-muted text-sm">
                  In: {s.startedAt ? fmtDate(s.startedAt) : "—"}
                </Text>
                <Text className="text-muted text-sm">
                  Out: {s.endedAt ? fmtDate(s.endedAt) : "—"}
                </Text>
              </View>
            </Card>
          ))
        )}
      </Screen>
    </ScrollView>
  );
}

import { useEffect, useState } from "react";
import { View, Text, Pressable, ScrollView } from "react-native";
import { Ionicons } from "@expo/vector-icons";
import { Screen, Card, PrimaryButton, SectionTitle, IconBadge } from "../components/ui";
import { useApp, formatFee } from "../context/AppContext";
import type { ParkingFacility } from "../types";
import { api } from "../services/mockApi";
import type { TabScreenProps } from "../navigation/types";

export function DashboardScreen({ navigation }: TabScreenProps<"Home">) {
  const { user, vehicles, activeSession } = useApp();
  const [facility, setFacility] = useState<ParkingFacility | null>(null);

  useEffect(() => {
    if (!activeSession) return;
    void api.getFacility("DW001").then((res) => {
      if (res.success) setFacility(res.data);
    });
  }, [activeSession]);

  return (
    <ScrollView className="flex-1" showsVerticalScrollIndicator={false} contentContainerClassName="pb-36">
      <Screen>
        <SectionTitle title={`Hi, ${user?.name ?? "there"} 👋`} subtitle="Here's your parking at a glance." />

        {activeSession ? (
          <Pressable onPress={() => navigation.navigate("ActiveSession", { sessionId: activeSession.id })}>
            <Card>
              <View className="flex-row items-center mb-4">
                <IconBadge icon={activeSession.status === "CREATED" ? "log-in" : "car"} />
                <View className="ml-4 flex-1">
                  <Text className="text-primary font-bold text-base">Active session</Text>
                  <Text className="text-muted text-sm mt-0.5">
                    {facility?.name ?? "Loading facility…"}
                  </Text>
                </View>
                <View className="bg-brand-tint rounded-full px-3 py-1">
                  <Text className="text-brand text-xs font-semibold">{activeSession.status}</Text>
                </View>
              </View>
              {activeSession.status === "CREATED" ? (
                <Text className="text-muted text-sm leading-relaxed mb-4">
                  Hold your phone near the gate and tap "Open entry gate" when you're ready to drive in.
                </Text>
              ) : (
                <Text className="text-muted text-sm leading-relaxed mb-4">
                  Meter is running — fee updates every second while you're parked.
                </Text>
              )}
              <PrimaryButton label="Open Session" onPress={() => navigation.navigate("ActiveSession", { sessionId: activeSession.id })} />
            </Card>
          </Pressable>
        ) : (
          <Card>
            <View className="items-center py-4">
              <IconBadge icon="car-sport" size={32} />
              <Text className="text-primary font-bold text-base mt-4 mb-1">No active session</Text>
              <Text className="text-muted text-sm leading-relaxed text-center mb-4">
                Walk or drive up to a Dwara gate, find it in the Find tab, and start a session.
              </Text>
              <PrimaryButton label="Find a Facility" onPress={() => navigation.navigate("Discover")} />
            </View>
          </Card>
        )}

        <SectionTitle title="Your vehicles" />
        {vehicles.map((v) => (
          <Card key={v.id}>
            <View className="flex-row items-center">
              <IconBadge icon={v.make === "Honda" ? "bicycle" : "car"} />
              <View className="ml-4 flex-1">
                <Text className="text-primary font-bold text-base">{v.licensePlate}</Text>
                <Text className="text-muted text-sm mt-0.5">
                  {v.make} {v.model} · {v.color}
                </Text>
              </View>
              <Ionicons name="chevron-forward" size={20} color="#6B7280" />
            </View>
          </Card>
        ))}

        <Pressable onPress={() => navigation.navigate("Profile")}>
          <Card className="bg-brand-tint">
            <View className="flex-row items-center justify-between">
              <View className="flex-1 pr-4">
                <Text className="text-primary font-bold text-base mb-1">Add a vehicle</Text>
                <Text className="text-muted text-sm leading-relaxed">
                  Register a plate once — it works at every Dwara gate.
                </Text>
              </View>
              <Ionicons name="add-circle" size={36} color="#00B177" />
            </View>
          </Card>
        </Pressable>
      </Screen>
    </ScrollView>
  );
}

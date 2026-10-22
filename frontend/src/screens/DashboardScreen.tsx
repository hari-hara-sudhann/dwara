import { View, Text, Pressable, ScrollView } from "react-native";
import { Ionicons } from "@expo/vector-icons";
import { Screen, Card, PrimaryButton, SectionTitle, IconBadge } from "../components/ui";
import { useApp, vehicleTypeLabel } from "../context/AppContext";
import type { TabScreenProps } from "../navigation/types";

export function DashboardScreen({ navigation }: TabScreenProps<"Home">) {
  const { user, vehicles, activeSession } = useApp();

  return (
    <ScrollView className="flex-1" showsVerticalScrollIndicator={false} contentContainerClassName="pb-36">
      <Screen>
        <SectionTitle
          title={`Hi, ${user?.name ?? "there"} 👋`}
          subtitle="Here's your parking at a glance."
        />

        {activeSession ? (
          <Pressable
            onPress={() =>
              navigation.navigate("ActiveSession", { sessionId: activeSession.sessionId })
            }
          >
            <Card>
              <View className="flex-row items-center mb-4">
                <IconBadge icon={activeSession.sessionStatus === "CREATED" ? "log-in" : "car"} />
                <View className="ml-4 flex-1">
                  <Text className="text-primary font-bold text-base">Active session</Text>
                  <Text className="text-muted text-sm mt-0.5">
                    {activeSession.facilityName}
                  </Text>
                </View>
                <View className="bg-brand-tint rounded-full px-3 py-1">
                  <Text className="text-brand text-xs font-semibold">
                    {activeSession.sessionStatus}
                  </Text>
                </View>
              </View>
              {activeSession.sessionStatus === "CREATED" ? (
                <Text className="text-muted text-sm leading-relaxed mb-4">
                  Hold your phone near the gate and tap "Open entry gate" when you're ready to
                  drive in.
                </Text>
              ) : (
                <Text className="text-muted text-sm leading-relaxed mb-4">
                  Meter is running — the fee updates from the backend while you're parked.
                </Text>
              )}
              <PrimaryButton
                label="Open Session"
                onPress={() =>
                  navigation.navigate("ActiveSession", { sessionId: activeSession.sessionId })
                }
              />
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
        {vehicles.length === 0 ? (
          <Card>
            <Text className="text-muted text-sm leading-relaxed">
              No vehicles yet — add a number plate in Profile.
            </Text>
          </Card>
        ) : (
          vehicles.map((v) => (
            <Card key={v.vehicleId}>
              <View className="flex-row items-center">
                <IconBadge icon={v.type === "MC" ? "bicycle" : "car"} />
                <View className="ml-4 flex-1">
                  <Text className="text-primary font-bold text-base">{v.numberPlate}</Text>
                  <Text className="text-muted text-sm mt-0.5">{vehicleTypeLabel(v.type)}</Text>
                </View>
                <Ionicons name="chevron-forward" size={20} color="#6B7280" />
              </View>
            </Card>
          ))
        )}

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

import { useEffect, useState } from "react";
import { View, Text, Pressable, ScrollView, Alert } from "react-native";
import { Ionicons } from "@expo/vector-icons";
import { Screen, Card, SectionTitle, PrimaryButton, IconBadge } from "../components/ui";
import { api } from "../services/mockApi";
import { useApp } from "../context/AppContext";
import type { ParkingFacility } from "../types";
import type { StackScreenProps } from "../navigation/types";

export function StartSessionScreen({ route, navigation }: StackScreenProps<"StartSession">) {
  const { vehicles, refreshActiveSession } = useApp();
  const facilityCode = route.params.facilityCode;
  const [facility, setFacility] = useState<ParkingFacility | null>(null);
  const [selectedVehicleId, setSelectedVehicleId] = useState<string | null>(vehicles[0]?.id ?? null);
  const [creating, setCreating] = useState(false);

  useEffect(() => {
    api.getFacility(facilityCode).then((res) => {
      if (res.success) setFacility(res.data);
    });
  }, [facilityCode]);

  const start = async () => {
    if (!selectedVehicleId) return;
    setCreating(true);
    const res = await api.createSession({ facilityCode, vehicleId: selectedVehicleId });
    setCreating(false);
    if (!res.success) {
      Alert.alert("Can't start session", res.message);
      return;
    }
    await refreshActiveSession();
    navigation.navigate("ActiveSession", { sessionId: res.data.id });
  };

  return (
    <ScrollView className="flex-1" showsVerticalScrollIndicator={false}>
      <Screen>
        <SectionTitle title="Start session" subtitle="Pick the vehicle you're driving in today." />

        {facility ? (
          <Card>
            <View className="flex-row items-center mb-2">
              <IconBadge icon="location" />
              <View className="ml-4 flex-1">
                <Text className="text-primary font-bold text-base">{facility.name}</Text>
                <Text className="text-muted text-sm mt-0.5">{facility.address}</Text>
              </View>
            </View>
          </Card>
        ) : (
          <Card>
            <Text className="text-muted text-sm leading-relaxed">Loading facility…</Text>
          </Card>
        )}

        <SectionTitle title="Vehicle" />
        {vehicles.map((v) => {
          const selected = v.id === selectedVehicleId;
          return (
            <Pressable key={v.id} onPress={() => setSelectedVehicleId(v.id)}>
              <Card className={selected ? "border border-brand" : ""}>
                <View className="flex-row items-center">
                  <IconBadge icon="car" />
                  <View className="ml-4 flex-1">
                    <Text className="text-primary font-bold text-base">{v.licensePlate}</Text>
                    <Text className="text-muted text-sm mt-0.5">
                      {v.make} {v.model}
                    </Text>
                  </View>
                  <Ionicons
                    name={selected ? "checkmark-circle" : "ellipse-outline"}
                    size={24}
                    color={selected ? "#00B177" : "#6B7280"}
                  />
                </View>
              </Card>
            </Pressable>
          );
        })}

        {facility ? (
          <Card>
            <View className="flex-row justify-between">
              <View className="items-center flex-1">
                <Text className="text-primary font-bold text-sm">
                  ₹{(facility.ratePerSecond * 3600).toFixed(0)}/hr
                </Text>
                <Text className="text-muted text-xs mt-0.5">flat rate</Text>
              </View>
              <View className="items-center flex-1">
                <Text className="text-primary font-bold text-sm">per-second</Text>
                <Text className="text-muted text-xs mt-0.5">billing</Text>
              </View>
              <View className="items-center flex-1">
                <Text className="text-primary font-bold text-sm">{facility.availableSlots}</Text>
                <Text className="text-muted text-xs mt-0.5">free slots</Text>
              </View>
            </View>
          </Card>
        ) : null}

        <PrimaryButton
          label="Create Session"
          onPress={start}
          disabled={!selectedVehicleId || !facility}
          loading={creating}
        />
        <View className="h-2" />
        <Pressable onPress={() => navigation.goBack()} className="h-12 items-center justify-center">
          <Text className="text-muted text-sm">Back</Text>
        </Pressable>
      </Screen>
    </ScrollView>
  );
}

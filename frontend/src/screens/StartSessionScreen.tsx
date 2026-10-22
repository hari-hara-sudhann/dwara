import { useEffect, useState } from "react";
import { View, Text, Pressable, ScrollView, Alert } from "react-native";
import { Ionicons } from "@expo/vector-icons";
import { Screen, Card, SectionTitle, PrimaryButton, IconBadge } from "../components/ui";
import { api } from "../services/api";
import { useApp, hourlyRate, ratePerSecond, vehicleTypeLabel } from "../context/AppContext";
import type { ParkingFacility } from "../types";
import type { StackScreenProps } from "../navigation/types";

export function StartSessionScreen({ route, navigation }: StackScreenProps<"StartSession">) {
  const { vehicles, refreshActiveSession } = useApp();
  const facilityCode = route.params.facilityCode;
  const [facility, setFacility] = useState<ParkingFacility | null>(null);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [selectedVehicleId, setSelectedVehicleId] = useState<string | null>(
    vehicles[0]?.vehicleId ?? null
  );
  const [creating, setCreating] = useState(false);

  useEffect(() => {
    let cancelled = false;
    void api.getFacility(facilityCode).then((res) => {
      if (cancelled) return;
      if (res.success) setFacility(res.data);
      else setLoadError(res.message);
    });
    return () => {
      cancelled = true;
    };
  }, [facilityCode]);

  const selectedVehicle = vehicles.find((v) => v.vehicleId === selectedVehicleId) ?? null;

  const start = async () => {
    if (!selectedVehicleId || creating) return;
    setCreating(true);
    const res = await api.createSession({ facilityCode, vehicleId: selectedVehicleId });
    setCreating(false);
    if (!res.success) {
      Alert.alert("Can't start session", res.message);
      return;
    }
    // POST /sessions returns only the new UUID; fetch the full session.
    await refreshActiveSession();
    navigation.navigate("ActiveSession", { sessionId: res.data });
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
            {facility.status !== "ACTIVE" ? (
              <Text className="text-red-500 text-sm mt-2">
                This facility is {facility.status.toLowerCase()} and is not accepting sessions.
              </Text>
            ) : null}
          </Card>
        ) : (
          <Card>
            <Text className="text-muted text-sm leading-relaxed">
              {loadError ?? "Loading facility…"}
            </Text>
          </Card>
        )}

        <SectionTitle title="Vehicle" />
        {vehicles.length === 0 ? (
          <Card>
            <Text className="text-primary font-bold text-base mb-1">No vehicles yet</Text>
            <Text className="text-muted text-sm leading-relaxed mb-4">
              Register a number plate in Profile before starting a session.
            </Text>
            <PrimaryButton
              label="Go to Profile"
              onPress={() => navigation.navigate("Tabs", { screen: "Profile" })}
            />
          </Card>
        ) : (
          vehicles.map((v) => {
            const selected = v.vehicleId === selectedVehicleId;
            return (
              <Pressable key={v.vehicleId} onPress={() => setSelectedVehicleId(v.vehicleId)}>
                <Card className={selected ? "border border-brand" : ""}>
                  <View className="flex-row items-center">
                    <IconBadge icon="car" />
                    <View className="ml-4 flex-1">
                      <Text className="text-primary font-bold text-base">{v.numberPlate}</Text>
                      <Text className="text-muted text-sm mt-0.5">{vehicleTypeLabel(v.type)}</Text>
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
          })
        )}

        {facility && selectedVehicle ? (
          <Card>
            <View className="flex-row justify-between">
              <View className="items-center flex-1">
                <Text className="text-primary font-bold text-sm">
                  {hourlyRate(ratePerSecond(facility, selectedVehicle.type))}
                </Text>
                <Text className="text-muted text-xs mt-0.5">your rate</Text>
              </View>
              <View className="items-center flex-1">
                <Text className="text-primary font-bold text-sm">per-second</Text>
                <Text className="text-muted text-xs mt-0.5">billing</Text>
              </View>
              <View className="items-center flex-1">
                <Text className="text-primary font-bold text-sm">{facility.status}</Text>
                <Text className="text-muted text-xs mt-0.5">facility</Text>
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

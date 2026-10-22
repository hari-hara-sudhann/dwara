import { useState } from "react";
import { View, Text, Pressable, ScrollView, ActivityIndicator } from "react-native";
import { Ionicons } from "@expo/vector-icons";
import { Screen, Card, PrimaryButton, SectionTitle } from "../components/ui";
import { realBle } from "../services/realBle";
import { api } from "../services/api";
import { hourlyRate } from "../context/AppContext";
import type { ParkingFacility } from "../types";
import type { TabScreenProps } from "../navigation/types";

type ScanState = "idle" | "scanning" | "done" | "error";

export function DiscoverScreen({ navigation }: TabScreenProps<"Discover">) {
  const [state, setState] = useState<ScanState>("idle");
  const [facilities, setFacilities] = useState<ParkingFacility[]>([]);
  const [error, setError] = useState<string | null>(null);

  const scan = async () => {
    setState("scanning");
    setFacilities([]);
    setError(null);
    try {
      // Real BLE discovery: gates advertise as DWARA-<facilityCode>.
      const beacons = await realBle.scanFacilities(6);
      const resolved: ParkingFacility[] = [];
      for (const beacon of beacons) {
        const res = await api.getFacility(beacon.facilityCode);
        if (res.success) resolved.push(res.data);
      }
      setFacilities(resolved);
      setState("done");
    } catch (e) {
      setError((e as Error).message);
      setState("error");
    }
  };

  const scanning = state === "scanning";

  return (
    <ScrollView className="flex-1" showsVerticalScrollIndicator={false} contentContainerClassName="pb-36">
      <Screen>
        <SectionTitle title="Find a facility" subtitle="Scan for Dwara gates broadcasting nearby over BLE." />

        <Card>
          <View className="items-center py-2">
            <View className="bg-brand-tint rounded-full p-3 items-center justify-center mb-4">
              <Ionicons name="locate" size={32} color="#00B177" />
            </View>
            <Text className="text-primary font-bold text-base mb-1">DWARA- beacons</Text>
            <Text className="text-muted text-sm leading-relaxed text-center mb-4">
              Each gate advertises as DWARA-&lt;facilityCode&gt;. Get within a few metres of the
              gate and scan.
            </Text>
            {scanning ? (
              <View className="h-12 items-center justify-center">
                <ActivityIndicator color="#00B177" />
              </View>
            ) : (
              <PrimaryButton label={state === "idle" ? "Start Scan" : "Scan Again"} onPress={scan} />
            )}
          </View>
        </Card>

        {error ? (
          <Card>
            <Text className="text-primary font-bold text-base mb-1">Bluetooth problem</Text>
            <Text className="text-muted text-sm leading-relaxed">{error}</Text>
          </Card>
        ) : null}

        {state === "done" && facilities.length === 0 ? (
          <Card>
            <View className="items-center py-2">
              <Text className="text-primary font-bold text-base mb-1">No gates found</Text>
              <Text className="text-muted text-sm leading-relaxed text-center">
                Move closer to the entrance and scan again.
              </Text>
            </View>
          </Card>
        ) : null}

        {facilities.map((f) => (
          <Pressable
            key={f.facilityId}
            onPress={() => navigation.navigate("StartSession", { facilityCode: f.facilityCode })}
          >
            <Card>
              <View className="flex-row items-center mb-4">
                <View className="bg-brand-tint rounded-full p-3 items-center justify-center">
                  <Ionicons name="location" size={24} color="#00B177" />
                </View>
                <View className="ml-4 flex-1">
                  <Text className="text-primary font-bold text-base">{f.name}</Text>
                  <Text className="text-muted text-sm mt-0.5">{f.address}</Text>
                </View>
              </View>
              <View className="flex-row justify-between">
                <View className="items-center flex-1">
                  <Text className="text-primary font-bold text-sm">
                    {hourlyRate(f.pricingPolicy.lmvRate)}
                  </Text>
                  <Text className="text-muted text-xs mt-0.5">car rate</Text>
                </View>
                <View className="items-center flex-1">
                  <Text className="text-primary font-bold text-sm">
                    {hourlyRate(f.pricingPolicy.mcRate)}
                  </Text>
                  <Text className="text-muted text-xs mt-0.5">bike rate</Text>
                </View>
                <View className="items-center flex-1">
                  <Text className="text-primary font-bold text-sm">{f.facilityCode}</Text>
                  <Text className="text-muted text-xs mt-0.5">gate code</Text>
                </View>
              </View>
            </Card>
          </Pressable>
        ))}
      </Screen>
    </ScrollView>
  );
}

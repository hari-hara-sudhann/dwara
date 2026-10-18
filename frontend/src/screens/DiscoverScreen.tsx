import { useState } from "react";
import { View, Text, Pressable, ScrollView, ActivityIndicator } from "react-native";
import { Ionicons } from "@expo/vector-icons";
import { Screen, Card, PrimaryButton, SectionTitle } from "../components/ui";
import { ble } from "../services/mockBle";
import { api } from "../services/mockApi";
import type { ParkingFacility } from "../types";
import type { TabScreenProps } from "../navigation/types";

export function DiscoverScreen({ navigation }: TabScreenProps<"Discover">) {
  const [scanning, setScanning] = useState(false);
  const [facilities, setFacilities] = useState<ParkingFacility[]>([]);
  const [scanned, setScanned] = useState(false);

  const scan = async () => {
    setScanning(true);
    setFacilities([]);
    const beacons = await ble.scanFacilities();
    const resolved: ParkingFacility[] = [];
    for (const b of beacons) {
      const res = await api.getFacility(b.facilityCode);
      if (res.success) resolved.push(res.data);
    }
    setFacilities(resolved);
    setScanning(false);
    setScanned(true);
  };

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
              Each gate advertises as DWARA-&lt;facilityCode&gt;. Get within a few metres of the gate and scan.
            </Text>
            {scanning ? (
              <View className="h-12 items-center justify-center">
                <ActivityIndicator color="#00B177" />
              </View>
            ) : (
              <PrimaryButton label={scanned ? "Scan Again" : "Start Scan"} onPress={scan} />
            )}
          </View>
        </Card>

        {scanned && facilities.length === 0 && !scanning ? (
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
          <Pressable key={f.id} onPress={() => navigation.navigate("StartSession", { facilityCode: f.facilityCode })}>
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
              {/* Spec grid: metrics in a flex-row justify-between badge layout. */}
              <View className="flex-row justify-between">
                <View className="items-center flex-1">
                  <Text className="text-primary font-bold text-sm">{f.availableSlots}</Text>
                  <Text className="text-muted text-xs mt-0.5">free slots</Text>
                </View>
                <View className="items-center flex-1">
                  <Text className="text-primary font-bold text-sm">
                    ₹{(f.ratePerSecond * 3600).toFixed(0)}/hr
                  </Text>
                  <Text className="text-muted text-xs mt-0.5">flat rate</Text>
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

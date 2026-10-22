import { useState } from "react";
import { View, Text, ScrollView, Pressable, Alert } from "react-native";
import { Ionicons } from "@expo/vector-icons";
import { Screen, Card, SectionTitle, IconBadge, PrimaryButton, Input } from "../components/ui";
import { useApp, vehicleTypeLabel } from "../context/AppContext";
import { api } from "../services/api";
import type { VehicleType } from "../types";
import type { TabScreenProps } from "../navigation/types";

const TYPES: VehicleType[] = ["MC", "LMV", "HMV"];

export function ProfileScreen({ navigation }: TabScreenProps<"Profile">) {
  const { user, vehicles, refreshVehicles, signOut } = useApp();
  const [adding, setAdding] = useState(false);
  const [plate, setPlate] = useState("");
  const [type, setType] = useState<VehicleType>("LMV");
  const [saving, setSaving] = useState(false);

  const saveVehicle = async () => {
    if (!plate || saving) return;
    setSaving(true);
    const res = await api.createVehicle({ numberPlate: plate.trim().toUpperCase(), type });
    setSaving(false);
    if (res.success) {
      setPlate("");
      setAdding(false);
      await refreshVehicles();
    } else {
      Alert.alert("Couldn't add vehicle", res.message);
    }
  };

  const removeVehicle = (vehicleId: string, label: string) => {
    Alert.alert("Remove vehicle", `Delete ${label}?`, [
      { text: "Cancel", style: "cancel" },
      {
        text: "Remove",
        style: "destructive",
        onPress: async () => {
          const res = await api.deleteVehicle(vehicleId);
          if (!res.success) {
            Alert.alert("Can't remove vehicle", res.message);
            return;
          }
          await refreshVehicles();
        },
      },
    ]);
  };

  return (
    <ScrollView className="flex-1" showsVerticalScrollIndicator={false} contentContainerClassName="pb-36">
      <Screen>
        <SectionTitle title="Profile" subtitle="Account details and registered vehicles." />

        <Card>
          <View className="flex-row items-center">
            <IconBadge icon="person" size={28} />
            <View className="ml-4 flex-1">
              <Text className="text-primary font-bold text-base">{user?.name}</Text>
              <Text className="text-muted text-sm mt-0.5">{user?.email}</Text>
            </View>
          </View>
        </Card>

        <SectionTitle title="Vehicles" />

        {vehicles.map((v) => (
          <Card key={v.vehicleId}>
            <View className="flex-row items-center">
              <IconBadge icon={v.type === "MC" ? "bicycle" : "car"} />
              <View className="ml-4 flex-1">
                <Text className="text-primary font-bold text-base">{v.numberPlate}</Text>
                <Text className="text-muted text-sm mt-0.5">{vehicleTypeLabel(v.type)}</Text>
              </View>
              <Pressable onPress={() => removeVehicle(v.vehicleId, v.numberPlate)} className="p-2">
                <Ionicons name="trash-outline" size={20} color="#6B7280" />
              </Pressable>
            </View>
          </Card>
        ))}

        {adding ? (
          <Card>
            <Input
              label="Number plate"
              placeholder="KA 05 MJ 4821"
              autoCapitalize="characters"
              value={plate}
              onChangeText={setPlate}
            />
            <Text className="text-muted text-sm mb-2">Vehicle type</Text>
            <View className="flex-row mb-4">
              {TYPES.map((t) => {
                const selected = type === t;
                return (
                  <Pressable
                    key={t}
                    onPress={() => setType(t)}
                    className={`flex-1 h-11 mr-2 rounded-btn items-center justify-center ${
                      selected ? "bg-brand" : "bg-brand-tint"
                    }`}
                  >
                    <Text className={`text-sm font-semibold ${selected ? "text-white" : "text-brand"}`}>
                      {t}
                    </Text>
                  </Pressable>
                );
              })}
            </View>
            <PrimaryButton
              label={saving ? "Saving…" : "Save Vehicle"}
              onPress={saveVehicle}
              loading={saving}
            />
            <View className="h-2" />
            <Pressable onPress={() => setAdding(false)} className="h-12 items-center justify-center">
              <Text className="text-muted text-sm">Cancel</Text>
            </Pressable>
          </Card>
        ) : (
          <PrimaryButton label="Add Vehicle" onPress={() => setAdding(true)} />
        )}

        <View className="h-6" />
        <Card>
          <Pressable
            onPress={() => navigation.navigate("GateTest")}
            className="flex-row items-center justify-between py-1"
          >
            <View className="flex-row items-center">
              <Ionicons name="construct-outline" size={20} color="#6B7280" />
              <Text className="text-muted text-base font-semibold ml-2">Gate Test (bench)</Text>
            </View>
            <Ionicons name="chevron-forward" size={20} color="#6B7280" />
          </Pressable>
          <Pressable
            onPress={() => void signOut()}
            className="flex-row items-center justify-center py-1 mt-2"
          >
            <Ionicons name="log-out-outline" size={20} color="#6B7280" />
            <Text className="text-muted text-base font-semibold ml-2">Sign Out</Text>
          </Pressable>
        </Card>
      </Screen>
    </ScrollView>
  );
}

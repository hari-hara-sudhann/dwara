import { useState } from "react";
import { View, Text, ScrollView, Pressable, Alert } from "react-native";
import { Ionicons } from "@expo/vector-icons";
import { Screen, Card, SectionTitle, IconBadge, PrimaryButton, Input } from "../components/ui";
import { useApp } from "../context/AppContext";
import { api } from "../services/mockApi";

export function ProfileScreen({ navigation }: { navigation: { navigate: (s: string) => void } }) {
  const { user, vehicles, refreshVehicles, signOut } = useApp();
  const [adding, setAdding] = useState(false);
  const [plate, setPlate] = useState("");
  const [make, setMake] = useState("");
  const [model, setModel] = useState("");
  const [saving, setSaving] = useState(false);

  const saveVehicle = async () => {
    if (!plate || !make || !model) return;
    setSaving(true);
    const res = await api.createVehicle({ licensePlate: plate, make, model, color: "—" });
    setSaving(false);
    if (res.success) {
      setPlate("");
      setMake("");
      setModel("");
      setAdding(false);
      await refreshVehicles();
    } else {
      Alert.alert("Couldn't add vehicle", res.message);
    }
  };

  const removeVehicle = (id: string, label: string) => {
    Alert.alert("Remove vehicle", `Delete ${label}?`, [
      { text: "Cancel", style: "cancel" },
      {
        text: "Remove",
        style: "destructive",
        onPress: () => {
          void api.deleteVehicle(id).then(() => refreshVehicles());
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
              <Text className="text-muted text-sm">{user?.phone}</Text>
            </View>
          </View>
        </Card>

        <SectionTitle title="Vehicles" />

        {vehicles.map((v) => (
          <Card key={v.id}>
            <View className="flex-row items-center">
              <IconBadge icon="car" />
              <View className="ml-4 flex-1">
                <Text className="text-primary font-bold text-base">{v.licensePlate}</Text>
                <Text className="text-muted text-sm mt-0.5">
                  {v.make} {v.model}
                </Text>
              </View>
              <Pressable onPress={() => removeVehicle(v.id, v.licensePlate)} className="p-2">
                <Ionicons name="trash-outline" size={20} color="#6B7280" />
              </Pressable>
            </View>
          </Card>
        ))}

        {adding ? (
          <Card>
            <Input label="License plate" placeholder="KA 05 MJ 4821" autoCapitalize="characters" value={plate} onChangeText={setPlate} />
            <Input label="Make" placeholder="Honda" value={make} onChangeText={setMake} />
            <Input label="Model" placeholder="Activa 6G" value={model} onChangeText={setModel} />
            <PrimaryButton label={saving ? "Saving…" : "Save Vehicle"} onPress={saveVehicle} />
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
              <Text className="text-muted text-base font-semibold ml-2">Gate Test (no backend)</Text>
            </View>
            <Ionicons name="chevron-forward" size={20} color="#6B7280" />
          </Pressable>
          <Pressable onPress={signOut} className="flex-row items-center justify-center py-1 mt-2">
            <Ionicons name="log-out-outline" size={20} color="#6B7280" />
            <Text className="text-muted text-base font-semibold ml-2">Sign Out</Text>
          </Pressable>
        </Card>
      </Screen>
    </ScrollView>
  );
}

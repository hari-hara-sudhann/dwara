import { View, Text } from "react-native";
import { Ionicons } from "@expo/vector-icons";
import { Screen, Hero, PrimaryButton, GhostButton } from "../components/ui";
import type { StackScreenProps } from "../navigation/types";

/** Welcome / Onboarding — centered title, hero on top, single CTA path. */
export function WelcomeScreen({ navigation }: StackScreenProps<"Welcome">) {
  const features: { icon: keyof typeof Ionicons.glyphMap; title: string; sub: string }[] = [
    { icon: "bluetooth", title: "Tap & Enter", sub: "BLE gate control" },
    { icon: "time", title: "Per-second", sub: "Fair INR billing" },
    { icon: "wallet", title: "Mock Pay", sub: "One-tap exit" },
  ];

  return (
    <Screen>
      <View className="flex-1">
        <Hero icon="car-sport" />

        <View className="items-center mt-2">
          <Text className="text-primary text-2xl font-bold tracking-tight mb-2 text-center">
            Parking, minus the{"\n"}paper ticket
          </Text>
          <Text className="text-muted text-sm leading-relaxed text-center px-6">
            Dwara opens the gate from your phone. No cameras, no cards — just
            drive in, park, and pay by the second.
          </Text>
        </View>

        {/* Spec badges: icon in brand-tint circle, bold primary, muted sublabel. */}
        <View className="flex-row justify-between mt-8 px-2">
          {features.map((f) => (
            <View key={f.title} className="items-center w-[30%]">
              <View className="bg-brand-tint rounded-full p-3 items-center justify-center mb-2">
                <Ionicons name={f.icon} size={22} color="#00B177" />
              </View>
              <Text className="text-primary font-bold text-sm">{f.title}</Text>
              <Text className="text-muted text-xs mt-0.5">{f.sub}</Text>
            </View>
          ))}
        </View>

        <View className="flex-1" />

        <View className="pb-6">
          <PrimaryButton label="Get Started" onPress={() => navigation.navigate("Register")} />
          <View className="h-4" />
          <GhostButton label="I already have an account" onPress={() => navigation.navigate("Login")} />
        </View>
      </View>
    </Screen>
  );
}

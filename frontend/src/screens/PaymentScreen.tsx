import { useEffect, useState } from "react";
import { View, Text, Pressable, ScrollView, Alert } from "react-native";
import { Ionicons } from "@expo/vector-icons";
import { Screen, Card, SectionTitle, PrimaryButton } from "../components/ui";
import { useApp, formatFee } from "../context/AppContext";
import { api } from "../services/mockApi";
import type { PaymentMethod } from "../types";
import type { StackScreenProps } from "../navigation/types";

/** Flat per-second INR rate shown for context (D14). */
const RATE_PER_SECOND = 0.05;

export function PaymentScreen({ route, navigation }: StackScreenProps<"Payment">) {
  const sessionId = route.params.sessionId;
  const [fee, setFee] = useState<number | null>(null);
  const [method, setMethod] = useState<PaymentMethod | null>(null);
  const [paying, setPaying] = useState(false);

  useEffect(() => {
    let cancelled = false;
    const tick = async () => {
      const res = await api.getSessionFee(sessionId);
      if (!cancelled && res.success) setFee(res.data.fee);
    };
    void tick();
    const t = setInterval(tick, 1000);
    return () => {
      cancelled = true;
      clearInterval(t);
    };
  }, [sessionId]);

  const methods: PaymentMethod[] = api.listMethods();

  const pay = async () => {
    if (!method) return;
    setPaying(true);
    const res = await api.paySession(sessionId, method.id);
    setPaying(false);
    if (!res.success) {
      Alert.alert("Payment failed", res.message);
      return;
    }
    // Mock gateway settled. Session is now PAYMENT_PENDING — exit gate next.
    navigation.navigate("ActiveSession", { sessionId });
  };

  return (
    <ScrollView className="flex-1" showsVerticalScrollIndicator={false}>
      <Screen>
        <SectionTitle title="Payment" subtitle="Fees accrue by the second until you pay." />

        <Card>
          <View className="items-center py-2 mb-4">
            <Text className="text-muted text-sm mb-2">Amount due</Text>
            <Text className="text-primary text-2xl font-bold tracking-tight">
              {fee === null ? "—" : formatFee(fee)}
            </Text>
            <Text className="text-muted text-sm mt-1">₹{(RATE_PER_SECOND * 3600).toFixed(0)}/hr · charged per second</Text>
          </View>
        </Card>

        <SectionTitle title="Pay with" />
        {methods.map((m) => {
          const selected = method?.id === m.id;
          return (
            <Pressable key={m.id} onPress={() => setMethod(m)}>
              <Card className={selected ? "border border-brand" : ""}>
                <View className="flex-row items-center">
                  <View className="bg-brand-tint rounded-full p-3 items-center justify-center">
                    <Ionicons
                      name={m.id === "upi" ? "phone-portrait" : "card"}
                      size={24}
                      color="#00B177"
                    />
                  </View>
                  <View className="ml-4 flex-1">
                    <Text className="text-primary font-bold text-base">{m.label}</Text>
                    <Text className="text-muted text-sm mt-0.5">{m.detail}</Text>
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

        <PrimaryButton
          label={fee === null ? "Pay" : `Pay ${formatFee(fee)}`}
          onPress={pay}
          disabled={!method}
          loading={paying}
        />
        <View className="h-2" />
        <Pressable onPress={() => navigation.goBack()} className="h-12 items-center justify-center mb-8">
          <Text className="text-muted text-sm">Not leaving yet</Text>
        </Pressable>
      </Screen>
    </ScrollView>
  );
}

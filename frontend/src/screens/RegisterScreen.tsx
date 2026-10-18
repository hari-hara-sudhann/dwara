import { useState } from "react";
import { View, Text, Pressable } from "react-native";
import { Screen, Card, Input, PrimaryButton, SectionTitle } from "../components/ui";
import { api } from "../services/mockApi";
import { useApp } from "../context/AppContext";
import type { StackScreenProps } from "../navigation/types";

export function RegisterScreen({ navigation }: StackScreenProps<"Register">) {
  const { signIn } = useApp();
  const [name, setName] = useState("");
  const [email, setEmail] = useState("");
  const [phone, setPhone] = useState("");
  const [password, setPassword] = useState("");
  const [loading, setLoading] = useState(false);

  const submit = async () => {
    if (!name || !email || !phone || !password) return;
    setLoading(true);
    const res = await api.register({ name, email, phone, password });
    setLoading(false);
    if (res.success) signIn(res.data.user);
  };

  return (
    <Screen>
      <View className="flex-1 justify-center">
        <SectionTitle
          title="Create your account"
          subtitle="One account for every Dwara facility."
          center
        />

        <Card>
          <Input label="Full name" placeholder="Hari Sharma" value={name} onChangeText={setName} />
          <Input
            label="Email"
            placeholder="you@example.com"
            autoCapitalize="none"
            keyboardType="email-address"
            value={email}
            onChangeText={setEmail}
          />
          <Input
            label="Phone"
            placeholder="+91 98765 43210"
            keyboardType="phone-pad"
            value={phone}
            onChangeText={setPhone}
          />
          <Input
            label="Password"
            placeholder="••••••••"
            secureTextEntry
            value={password}
            onChangeText={setPassword}
          />
          <PrimaryButton label="Sign Up" onPress={submit} loading={loading} />
        </Card>

        <View className="items-center">
          <Text className="text-muted text-sm mb-2">Already have an account?</Text>
          <Pressable onPress={() => navigation.navigate("Login")}>
            <Text className="text-brand text-base font-semibold">Sign in</Text>
          </Pressable>
        </View>
      </View>
    </Screen>
  );
}

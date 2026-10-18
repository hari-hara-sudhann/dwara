import { useState } from "react";
import { View, Text, Pressable } from "react-native";
import { Screen, Card, Input, PrimaryButton, SectionTitle, GhostButton } from "../components/ui";
import { api } from "../services/mockApi";
import { useApp } from "../context/AppContext";
import type { StackScreenProps } from "../navigation/types";

export function LoginScreen({ navigation }: StackScreenProps<"Login">) {
  const { signIn } = useApp();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [loading, setLoading] = useState(false);

  const submit = async () => {
    if (!email || !password) return;
    setLoading(true);
    const res = await api.login({ email, password });
    setLoading(false);
    if (res.success) signIn(res.data.user);
  };

  return (
    <Screen>
      <View className="flex-1 justify-center">
        <SectionTitle
          title="Welcome back"
          subtitle="Sign in to manage your parking sessions."
          center
        />

        <Card>
          <Input
            label="Email"
            placeholder="you@example.com"
            autoCapitalize="none"
            keyboardType="email-address"
            value={email}
            onChangeText={setEmail}
          />
          <Input
            label="Password"
            placeholder="••••••••"
            secureTextEntry
            value={password}
            onChangeText={setPassword}
          />
          <PrimaryButton label="Sign In" onPress={submit} loading={loading} />
        </Card>

        <View className="items-center">
          <Text className="text-muted text-sm mb-2">New to Dwara?</Text>
          <Pressable onPress={() => navigation.navigate("Register")}>
            <Text className="text-brand text-base font-semibold">Create an account</Text>
          </Pressable>
        </View>
      </View>
    </Screen>
  );
}

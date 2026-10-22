import { useState } from "react";
import { View, Text, Pressable } from "react-native";
import { Screen, Card, Input, PrimaryButton, SectionTitle } from "../components/ui";
import { api } from "../services/api";
import { useApp } from "../context/AppContext";
import type { StackScreenProps } from "../navigation/types";

export function LoginScreen({ navigation }: StackScreenProps<"Login">) {
  const { signIn } = useApp();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const submit = async () => {
    if (!email || !password || loading) return;
    setError(null);
    setLoading(true);
    try {
      const login = await api.login({ email: email.trim(), password });
      if (!login.success) {
        setError(login.message);
        return;
      }
      // Login only returns the token; /auth/me resolves the profile behind it.
      const me = await api.me();
      if (!me.success) {
        setError(me.message);
        return;
      }
      signIn(me.data);
    } finally {
      setLoading(false);
    }
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
          {error ? <Text className="text-red-500 text-sm mb-3">{error}</Text> : null}
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

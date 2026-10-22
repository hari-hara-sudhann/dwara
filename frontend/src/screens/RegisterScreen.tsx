import { useState } from "react";
import { View, Text, Pressable } from "react-native";
import { Screen, Card, Input, PrimaryButton, SectionTitle } from "../components/ui";
import { api } from "../services/api";
import { useApp } from "../context/AppContext";
import type { StackScreenProps } from "../navigation/types";

export function RegisterScreen({ navigation }: StackScreenProps<"Register">) {
  const { signIn } = useApp();
  const [name, setName] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const submit = async () => {
    if (!name || !email || !password || loading) return;
    setError(null);
    setLoading(true);
    try {
      const registered = await api.register({ name: name.trim(), email: email.trim(), password });
      if (!registered.success) {
        setError(registered.message);
        return;
      }
      // Registration does not return a token: log in with the same credentials.
      const login = await api.login({ email: email.trim(), password });
      if (!login.success) {
        setError(login.message);
        return;
      }
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
            label="Password"
            placeholder="••••••••"
            secureTextEntry
            value={password}
            onChangeText={setPassword}
          />
          {error ? <Text className="text-red-500 text-sm mb-3">{error}</Text> : null}
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

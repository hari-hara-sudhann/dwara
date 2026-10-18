import { View, Text, TextInput, TextInputProps, Pressable } from "react-native";
import { Ionicons } from "@expo/vector-icons";
import { ReactNode } from "react";

/** Screen canvas wrapper: `flex-1 bg-canvas pt-12 px-5` (design system §2A). */
export function Screen({ children }: { children: ReactNode }) {
  return <View className="flex-1 bg-canvas pt-12 px-5">{children}</View>;
}

/** White floating content panel: `bg-white rounded-card p-6 mb-4` (§2B). */
export function Card({
  children,
  className = "",
}: {
  children: ReactNode;
  className?: string;
}) {
  return <View className={`bg-white rounded-card p-6 mb-4 shadow-sm ${className}`}>{children}</View>;
}

/** Section header (sits raw on the canvas): left-aligned by default. */
export function SectionTitle({
  title,
  subtitle,
  center = false,
}: {
  title: string;
  subtitle?: string;
  center?: boolean;
}) {
  return (
    <View className={center ? "items-center mb-6" : "mb-4"}>
      <Text className="text-primary text-2xl font-bold tracking-tight mb-2">{title}</Text>
      {subtitle ? (
        <Text className={`text-muted text-sm leading-relaxed ${center ? "text-center" : ""}`}>
          {subtitle}
        </Text>
      ) : null}
    </View>
  );
}

/** Primary CTA: `bg-brand h-12 rounded-btn items-center justify-center w-full` (§2D). */
export function PrimaryButton({
  label,
  onPress,
  disabled = false,
  loading = false,
  className = "",
}: {
  label: string;
  onPress: () => void;
  disabled?: boolean;
  loading?: boolean;
  className?: string;
}) {
  return (
    <Pressable
      onPress={onPress}
      disabled={disabled || loading}
      className={`bg-brand h-12 rounded-btn items-center justify-center w-full ${disabled ? "opacity-40" : ""} ${className}`}
    >
      {loading ? (
        <Text className="text-white text-base font-semibold">Please wait…</Text>
      ) : (
        <Text className="text-white text-base font-semibold">{label}</Text>
      )}
    </Pressable>
  );
}

/** Secondary CTA for entry screens (ghost button, no harsh borders). */
export function GhostButton({ label, onPress }: { label: string; onPress: () => void }) {
  return (
    <Pressable onPress={onPress} className="h-12 rounded-btn items-center justify-center w-full">
      <Text className="text-brand text-base font-semibold">{label}</Text>
    </Pressable>
  );
}

/** Rounded input: `rounded-btn`, white card surface, no sharp corners. */
export function Input({ label, ...props }: TextInputProps & { label: string }) {
  return (
    <View className="mb-4">
      <Text className="text-muted text-sm mb-2">{label}</Text>
      <TextInput
        className="bg-white rounded-btn h-12 px-4 text-primary text-base"
        placeholderTextColor="#9CA3AF"
        {...props}
      />
    </View>
  );
}

/** Icon badge: icon inside `bg-brand-tint rounded-full p-3` (§2D grid building block). */
export function IconBadge({
  icon,
  size = 24,
}: {
  icon: keyof typeof Ionicons.glyphMap;
  size?: number;
}) {
  return (
    <View className="bg-brand-tint rounded-full p-3 items-center justify-center">
      <Ionicons name={icon} size={size} color="#00B177" />
    </View>
  );
}

/**
 * Hero graphic: isolated on transparent background, `w-full h-56` (§2A).
 * Composed from token-only shapes so no binary assets are needed yet; replace
 * with a transparent PNG (resize-mode-contain) when art is finalized.
 */
export function Hero({ icon }: { icon: keyof typeof Ionicons.glyphMap }) {
  return (
    <View className="w-full h-56 items-center justify-center">
      <View className="bg-brand-tint rounded-card p-10 items-center justify-center">
        <Ionicons name={icon} size={96} color="#00B177" />
      </View>
    </View>
  );
}

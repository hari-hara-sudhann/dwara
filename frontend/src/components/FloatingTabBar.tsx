import { View, Text, Pressable } from "react-native";
import { Ionicons } from "@expo/vector-icons";
import type { BottomTabBarProps } from "@react-navigation/bottom-tabs";

/**
 * Floating tab bar (design system §2E):
 * `absolute bottom-6 left-5 right-5 bg-white h-20 rounded-card flex-row items-center justify-around px-4`
 * Unfocused: line icons `text-muted`. Active: filled icons + label `text-brand`.
 */
export function FloatingTabBar({ state, descriptors, navigation }: BottomTabBarProps) {
  return (
    <View
      pointerEvents="box-none"
      className="absolute bottom-6 left-5 right-5 bg-white h-20 rounded-card flex-row items-center justify-around px-4 shadow-sm"
    >
      {state.routes.map((route, index) => {
        const { options } = descriptors[route.key];
        const label = (options.title ?? route.name) as string;
        const isFocused = state.index === index;

        const iconMap: Record<string, { active: keyof typeof Ionicons.glyphMap; inactive: keyof typeof Ionicons.glyphMap }> = {
          Home: { active: "home", inactive: "home-outline" },
          Find: { active: "locate", inactive: "locate-outline" },
          Activity: { active: "receipt", inactive: "receipt-outline" },
          Profile: { active: "person", inactive: "person-outline" },
        };
        const icons = iconMap[route.name] ?? { active: "ellipse", inactive: "ellipse-outline" };

        const onPress = () => {
          const event = navigation.emit({ type: "tabPress", target: route.key, canPreventDefault: true });
          if (!isFocused && !event.defaultPrevented) {
            navigation.navigate(route.name, route.params);
          }
        };

        return (
          <Pressable
            key={route.key}
            accessibilityRole="button"
            accessibilityState={isFocused ? { selected: true } : {}}
            accessibilityLabel={options.tabBarAccessibilityLabel}
            onPress={onPress}
            className="items-center justify-center py-2"
          >
            <Ionicons
              name={isFocused ? icons.active : icons.inactive}
              size={22}
              color={isFocused ? "#00B177" : "#6B7280"}
            />
            <Text className={`text-xs mt-1 ${isFocused ? "text-brand font-medium" : "text-muted"}`}>
              {label}
            </Text>
          </Pressable>
        );
      })}
    </View>
  );
}

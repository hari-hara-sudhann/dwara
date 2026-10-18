import type { BottomTabScreenProps } from "@react-navigation/bottom-tabs";
import type { CompositeScreenProps, NavigatorScreenParams } from "@react-navigation/native";
import type { NativeStackScreenProps } from "@react-navigation/native-stack";

export type RootStackParamList = {
  Welcome: undefined;
  Login: undefined;
  Register: undefined;
  Tabs: NavigatorScreenParams<TabParamList> | undefined;
  StartSession: { facilityCode: string };
  ActiveSession: { sessionId: string };
  Payment: { sessionId: string };
  GateTest: undefined;
};

export type TabParamList = {
  Home: undefined;
  Discover: undefined;
  Activity: undefined;
  Profile: undefined;
};

/** Props for a screen rendered inside the tab navigator (can bubble to the stack). */
export type TabScreenProps<K extends keyof TabParamList> = CompositeScreenProps<
  BottomTabScreenProps<TabParamList, K>,
  NativeStackScreenProps<RootStackParamList>
>;

/** Props for a screen rendered directly on the root stack. */
export type StackScreenProps<K extends keyof RootStackParamList> = NativeStackScreenProps<RootStackParamList, K>;

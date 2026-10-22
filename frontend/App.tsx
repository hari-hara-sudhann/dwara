import "react-native-gesture-handler";
import "nativewind";
import "./global.css";

import { View, ActivityIndicator } from "react-native";
import { NavigationContainer } from "@react-navigation/native";
import { createNativeStackNavigator } from "@react-navigation/native-stack";
import { createBottomTabNavigator } from "@react-navigation/bottom-tabs";
import { StatusBar } from "expo-status-bar";

import { AppProvider, useApp } from "./src/context/AppContext";
import { FloatingTabBar } from "./src/components/FloatingTabBar";
import type { RootStackParamList, TabParamList } from "./src/navigation/types";

import { WelcomeScreen } from "./src/screens/WelcomeScreen";
import { LoginScreen } from "./src/screens/LoginScreen";
import { RegisterScreen } from "./src/screens/RegisterScreen";
import { DashboardScreen } from "./src/screens/DashboardScreen";
import { DiscoverScreen } from "./src/screens/DiscoverScreen";
import { ActivityScreen } from "./src/screens/ActivityScreen";
import { ProfileScreen } from "./src/screens/ProfileScreen";
import { StartSessionScreen } from "./src/screens/StartSessionScreen";
import { ActiveSessionScreen } from "./src/screens/ActiveSessionScreen";
import { PaymentScreen } from "./src/screens/PaymentScreen";
import { GateTestScreen } from "./src/screens/GateTestScreen";

const Stack = createNativeStackNavigator<RootStackParamList>();
const Tab = createBottomTabNavigator<TabParamList>();

function Tabs() {
  return (
    <Tab.Navigator
      screenOptions={{ headerShown: false }}
      tabBar={(props) => <FloatingTabBar {...props} />}
    >
      <Tab.Screen name="Home" component={DashboardScreen} options={{ title: "Home" }} />
      <Tab.Screen name="Discover" component={DiscoverScreen} options={{ title: "Find" }} />
      <Tab.Screen name="Activity" component={ActivityScreen} options={{ title: "Activity" }} />
      <Tab.Screen name="Profile" component={ProfileScreen} options={{ title: "Profile" }} />
    </Tab.Navigator>
  );
}

function RootNavigator() {
  const { user, initializing } = useApp();

  // Hold the splash while the persisted JWT is validated, so a signed-in user
  // never sees the login screen flash on relaunch.
  if (initializing) {
    return (
      <View style={{ flex: 1, alignItems: "center", justifyContent: "center", backgroundColor: "#F8F9FA" }}>
        <ActivityIndicator color="#00B177" size="large" />
      </View>
    );
  }

  return (
    <Stack.Navigator screenOptions={{ headerShown: false, contentStyle: { backgroundColor: "#F8F9FA" } }}>
      {!user ? (
        <>
          <Stack.Screen name="Welcome" component={WelcomeScreen} />
          <Stack.Screen name="Login" component={LoginScreen} />
          <Stack.Screen name="Register" component={RegisterScreen} />
        </>
      ) : (
        <>
          <Stack.Screen name="Tabs" component={Tabs} />
          <Stack.Screen name="StartSession" component={StartSessionScreen} />
          <Stack.Screen name="ActiveSession" component={ActiveSessionScreen} />
          <Stack.Screen name="Payment" component={PaymentScreen} />
          <Stack.Screen name="GateTest" component={GateTestScreen} />
        </>
      )}
    </Stack.Navigator>
  );
}

export default function App() {
  return (
    <AppProvider>
      <NavigationContainer>
        <StatusBar style="dark" />
        <RootNavigator />
      </NavigationContainer>
    </AppProvider>
  );
}

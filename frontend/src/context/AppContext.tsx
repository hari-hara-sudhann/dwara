import { createContext, useCallback, useContext, useEffect, useState } from "react";
import { ReactNode } from "react";
import type { ParkingSession, User, Vehicle } from "../types";
import { api } from "../services/mockApi";

interface AppState {
  user: User | null;
  signIn: (user: User) => void;
  signOut: () => void;

  vehicles: Vehicle[];
  refreshVehicles: () => Promise<void>;

  activeSession: ParkingSession | null;
  setActiveSession: (s: ParkingSession | null) => void;
  refreshActiveSession: () => Promise<ParkingSession | null>;
}

const AppContext = createContext<AppState | null>(null);

export function AppProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null>(null);
  const [vehicles, setVehicles] = useState<Vehicle[]>([]);
  const [activeSession, setActiveSession] = useState<ParkingSession | null>(null);

  const refreshVehicles = useCallback(async () => {
    const res = await api.listVehicles();
    if (res.success) setVehicles(res.data);
  }, []);

  const refreshActiveSession = useCallback(async () => {
    const res = await api.getActiveSession();
    const session = res.success ? res.data : null;
    setActiveSession(session);
    return session;
  }, []);

  useEffect(() => {
    if (user) {
      void refreshVehicles();
      void refreshActiveSession();
    }
  }, [user, refreshVehicles, refreshActiveSession]);

  const signIn = useCallback((u: User) => setUser(u), []);
  const signOut = useCallback(() => {
    setUser(null);
    setActiveSession(null);
    setVehicles([]);
  }, []);

  return (
    <AppContext.Provider
      value={{
        user,
        signIn,
        signOut,
        vehicles,
        refreshVehicles,
        activeSession,
        setActiveSession,
        refreshActiveSession,
      }}
    >
      {children}
    </AppContext.Provider>
  );
}

export function useApp(): AppState {
  const ctx = useContext(AppContext);
  if (!ctx) throw new Error("useApp must be used inside AppProvider");
  return ctx;
}

/** ₹ formatting for INR fees. */
export function formatFee(fee: number): string {
  return `₹${fee.toFixed(2)}`;
}

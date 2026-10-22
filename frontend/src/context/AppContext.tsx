import { createContext, useCallback, useContext, useEffect, useState } from "react";
import { ReactNode } from "react";
import type {
  ParkingFacility,
  ParkingSession,
  User,
  Vehicle,
  VehicleType,
} from "../types";
import { api, loadToken, setUnauthorizedHandler } from "../services/api";

interface AppState {
  /** True while the persisted token is being checked on launch. */
  initializing: boolean;

  user: User | null;
  signIn: (user: User) => void;
  signOut: () => Promise<void>;

  vehicles: Vehicle[];
  refreshVehicles: () => Promise<void>;

  activeSession: ParkingSession | null;
  setActiveSession: (s: ParkingSession | null) => void;
  refreshActiveSession: () => Promise<ParkingSession | null>;
}

const AppContext = createContext<AppState | null>(null);

export function AppProvider({ children }: { children: ReactNode }) {
  const [initializing, setInitializing] = useState(true);
  const [user, setUser] = useState<User | null>(null);
  const [vehicles, setVehicles] = useState<Vehicle[]>([]);
  const [activeSession, setActiveSession] = useState<ParkingSession | null>(null);

  const refreshVehicles = useCallback(async () => {
    const res = await api.listVehicles();
    if (res.success) setVehicles(res.data?.vehicles ?? []);
  }, []);

  const refreshActiveSession = useCallback(async () => {
    const res = await api.getActiveSession();
    const session = res.success ? res.data : null;
    setActiveSession(session);
    return session;
  }, []);

  const signOut = useCallback(async () => {
    await api.logout();
    setUser(null);
    setActiveSession(null);
    setVehicles([]);
  }, []);

  // A 401 from any request means the token is gone/expired: drop the session
  // and let the navigator return to the login screen.
  useEffect(() => {
    setUnauthorizedHandler(() => {
      setUser(null);
      setActiveSession(null);
      setVehicles([]);
    });
    return () => setUnauthorizedHandler(null);
  }, []);

  // Restore a persisted JWT on launch and turn it back into a profile.
  useEffect(() => {
    let cancelled = false;
    (async () => {
      try {
        const token = await loadToken();
        if (token) {
          const me = await api.me();
          if (!cancelled && me.success) setUser(me.data);
        }
      } finally {
        if (!cancelled) setInitializing(false);
      }
    })();
    return () => {
      cancelled = true;
    };
  }, []);

  const signIn = useCallback((u: User) => setUser(u), []);

  useEffect(() => {
    if (user) {
      void refreshVehicles();
      void refreshActiveSession();
    }
  }, [user, refreshVehicles, refreshActiveSession]);

  return (
    <AppContext.Provider
      value={{
        initializing,
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
  return `₹${Number(fee).toFixed(2)}`;
}

export function vehicleTypeLabel(type: VehicleType): string {
  return type === "MC" ? "Two-wheeler" : type === "LMV" ? "Car" : "Heavy vehicle";
}

/** Per-second INR rate for a vehicle class. */
export function ratePerSecond(facility: ParkingFacility | null | undefined, type: VehicleType): number {
  if (!facility) return 0;
  switch (type) {
    case "MC":
      return facility.pricingPolicy.mcRate;
    case "HMV":
      return facility.pricingPolicy.hmvRate;
    default:
      return facility.pricingPolicy.lmvRate;
  }
}

/** "$36/hr" style label from a per-second rate. */
export function hourlyRate(perSecond: number): string {
  return `₹${(perSecond * 3600).toFixed(0)}/hr`;
}

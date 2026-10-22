/**
 * Real REST client for the Dwara backend.
 *
 * - Base URL comes from EXPO_PUBLIC_API_URL (see frontend/.env.example), with
 *   localhost as the simulator default. A physical iPhone needs the laptop's
 *   LAN address, e.g. EXPO_PUBLIC_API_URL=http://192.168.1.20:8080.
 * - The JWT is stored in AsyncStorage and attached as `Authorization: Bearer`.
 * - Business refusals arrive as HTTP 200 with `success: false`; that envelope
 *   is returned to the caller so screens can show `message`. A 401 clears the
 *   token and notifies the app, which returns to the login screen.
 */

import AsyncStorage from "@react-native-async-storage/async-storage";
import type {
  ParkingFacility,
  ParkingSession,
  PaymentReceipt,
  ResponseObject,
  SessionPage,
  SessionStatus,
  User,
  Vehicle,
  VehicleType,
} from "../types";

export const API_BASE_URL =
  process.env.EXPO_PUBLIC_API_URL?.replace(/\/$/, "") ?? "http://192.168.3.1:8080";

const TOKEN_KEY = "dwara.jwt";

let token: string | null = null;
let onUnauthorized: (() => void) | null = null;

export function setUnauthorizedHandler(handler: (() => void) | null): void {
  onUnauthorized = handler;
}

export async function loadToken(): Promise<string | null> {
  token = await AsyncStorage.getItem(TOKEN_KEY);
  return token;
}

export async function saveToken(jwt: string): Promise<void> {
  token = jwt;
  await AsyncStorage.setItem(TOKEN_KEY, jwt);
}

export async function clearToken(): Promise<void> {
  token = null;
  await AsyncStorage.removeItem(TOKEN_KEY);
}

const failure = <T,>(message: string): ResponseObject<T> => ({
  success: false,
  message,
  data: null as never,
});

async function request<T>(
  path: string,
  options: { method?: string; body?: unknown; auth?: boolean } = {}
): Promise<ResponseObject<T>> {
  const { method = "GET", body, auth = true } = options;
  const headers: Record<string, string> = { Accept: "application/json" };
  if (body !== undefined) headers["Content-Type"] = "application/json";
  if (auth && token) headers.Authorization = `Bearer ${token}`;

  let response: Response;
  try {
    response = await fetch(`${API_BASE_URL}${path}`, {
      method,
      headers,
      body: body === undefined ? undefined : JSON.stringify(body),
    });
  } catch {
    return failure(
      `Cannot reach the Dwara backend at ${API_BASE_URL}. Check that it is running and reachable from the phone.`
    );
  }

  if (response.status === 401 || response.status === 403) {
    await clearToken();
    onUnauthorized?.();
    return failure("Your session expired. Please sign in again.");
  }

  // 204 or an empty body: treat as a successful empty payload.
  const text = await response.text();
  if (!text) return { success: response.ok, message: "", data: null as never };

  try {
    const parsed = JSON.parse(text) as ResponseObject<T>;
    if (typeof parsed.success !== "boolean") {
      return failure(`Unexpected response from the backend (HTTP ${response.status}).`);
    }
    return parsed;
  } catch {
    return failure(`Unexpected response from the backend (HTTP ${response.status}).`);
  }
}

export const api = {
  // ------------------------------------------------------------------ auth --

  async register(body: { name: string; email: string; password: string }): Promise<
    ResponseObject<Omit<User, "userId"> & { userId: string }>
  > {
    return request("/auth/register", {
      method: "POST",
      auth: false,
      body: { name: body.name, email: body.email, rawPassword: body.password },
    });
  },

  async login(body: { email: string; password: string }): Promise<ResponseObject<{ jwt: string }>> {
    const result = await request<{ jwt: string }>("/auth/login", {
      method: "POST",
      auth: false,
      body,
    });
    if (result.success && result.data?.jwt) {
      await saveToken(result.data.jwt);
      return result;
    }
    return failure(result.message || "Invalid credentials.");
  },

  async me(): Promise<ResponseObject<User>> {
    return request<User>("/auth/me");
  },

  async logout(): Promise<void> {
    await clearToken();
  },

  // -------------------------------------------------------------- vehicles --

  async listVehicles(): Promise<ResponseObject<{ userId: string; vehicles: Vehicle[] }>> {
    return request("/vehicles");
  },

  async createVehicle(body: {
    numberPlate: string;
    type: VehicleType;
  }): Promise<ResponseObject<{ uuid: string }>> {
    return request("/vehicles", { method: "POST", body });
  },

  async deleteVehicle(vehicleId: string): Promise<ResponseObject<null>> {
    return request(`/vehicles/${vehicleId}`, { method: "DELETE" });
  },

  // ------------------------------------------------------------ facilities --

  async getFacility(facilityCode: string): Promise<ResponseObject<ParkingFacility>> {
    return request(`/facilities/${encodeURIComponent(facilityCode)}`);
  },

  // -------------------------------------------------------------- sessions --

  async createSession(body: {
    facilityCode: string;
    vehicleId: string;
  }): Promise<ResponseObject<string>> {
    return request("/sessions", { method: "POST", body });
  },

  async getActiveSession(): Promise<ResponseObject<ParkingSession>> {
    return request("/sessions/active");
  },

  async getFee(sessionId: string): Promise<ResponseObject<number>> {
    return request(`/sessions/${sessionId}/fee`);
  },

  async pay(sessionId: string): Promise<ResponseObject<PaymentReceipt>> {
    return request(`/sessions/${sessionId}/pay`, { method: "POST" });
  },

  async cancel(sessionId: string): Promise<ResponseObject<null>> {
    return request(`/sessions/${sessionId}/cancel`, { method: "POST" });
  },

  async listSessions(params: {
    status?: SessionStatus;
    page?: number;
    size?: number;
  } = {}): Promise<ResponseObject<SessionPage>> {
    const query = new URLSearchParams();
    if (params.status) query.set("status", params.status);
    query.set("page", String(params.page ?? 0));
    query.set("size", String(params.size ?? 20));
    return request(`/sessions?${query.toString()}`);
  },
};

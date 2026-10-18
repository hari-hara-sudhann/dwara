/**
 * Service interfaces. The app is UI-first: `mockApi.ts` / `mockBle.ts` implement
 * these with fake data. When the Spring Boot controllers land, swap in real
 * implementations (REST for ApiClient, react-native-ble-plx for BleClient)
 * without touching any screen.
 */

import type {
  ParkingFacility,
  ParkingSession,
  PaymentMethod,
  ResponseObject,
  User,
  Vehicle,
} from "../types";

export interface ApiClient {
  register(body: {
    name: string;
    email: string;
    phone: string;
    password: string;
  }): Promise<ResponseObject<{ token: string; user: User }>>;

  login(body: { email: string; password: string }): Promise<ResponseObject<{ token: string; user: User }>>;

  listVehicles(): Promise<ResponseObject<Vehicle[]>>;
  createVehicle(body: Omit<Vehicle, "id">): Promise<ResponseObject<Vehicle>>;
  deleteVehicle(id: string): Promise<ResponseObject<null>>;

  getFacility(facilityCode: string): Promise<ResponseObject<ParkingFacility>>;

  createSession(body: { facilityCode: string; vehicleId: string }): Promise<ResponseObject<ParkingSession>>;
  getActiveSession(): Promise<ResponseObject<ParkingSession | null>>;
  getSessionFee(sessionId: string): Promise<ResponseObject<{ fee: number }>>;
  paySession(sessionId: string, methodId: string): Promise<ResponseObject<ParkingSession>>;
  cancelSession(sessionId: string): Promise<ResponseObject<ParkingSession>>;

  listHistory(): Promise<ResponseObject<ParkingSession[]>>;

  /** Called after the gate physically opened for ENTRY (mock of backend side effect). */
  confirmEntry(sessionId: string): Promise<ResponseObject<ParkingSession>>;
  /** Called after successful payment + gate exit (mock of backend side effect). */
  confirmExit(sessionId: string): Promise<ResponseObject<ParkingSession>>;
}

/** BLE client matching the firmware protocol in firmware/dwara-gate/config.h. */
export interface BleClient {
  /** Scan for peripherals advertising as `DWARA-<facilityCode>`. */
  scanFacilities(scanSeconds?: number): Promise<{ facilityCode: string; deviceName: string }[]>;
  /** Write `{"sessionId":"<uuid>","intent":"ENTRY"|"EXIT"}` to the command characteristic. */
  sendGateCommand(facilityCode: string, sessionId: string, intent: "ENTRY" | "EXIT"): Promise<"OPENED" | "DENIED" | "FAULT">;
}

export interface PaymentMethodsProvider {
  listMethods(): PaymentMethod[];
}

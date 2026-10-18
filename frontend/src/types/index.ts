/**
 * Domain types mirroring the Dwara backend (see dwara-architecture-spec.md §5).
 */

/** Response envelope used by every backend endpoint. */
export interface ResponseObject<T> {
  success: boolean;
  message: string;
  data: T;
}

export type SessionStatus =
  | "CREATED"
  | "PARKED"
  | "PAYMENT_PENDING"
  | "EXITED"
  | "ABORTED";

export interface User {
  id: string;
  name: string;
  email: string;
  phone: string;
}

export interface Vehicle {
  id: string;
  licensePlate: string;
  make: string;
  model: string;
  color: string;
}

export interface ParkingFacility {
  id: string;
  facilityCode: string;
  name: string;
  address: string;
  /** Flat per-second INR rate (decision D14). */
  ratePerSecond: number;
  totalSlots: number;
  availableSlots: number;
}

export interface ParkingSession {
  id: string;
  vehicleId: string;
  facilityId: string;
  status: SessionStatus;
  /** Epoch ms when the vehicle entered; null until PARKED. Backend clock only (D19). */
  startedAt: number | null;
  endedAt: number | null;
  /** Current fee in paise? No — INR as a decimal number. */
  fee: number;
}

export interface PaymentMethod {
  id: string;
  label: string;
  detail: string;
}

/** Result of a gate BLE round-trip (firmware status characteristic). */
export type GateResult = "OPENED" | "DENIED" | "FAULT";

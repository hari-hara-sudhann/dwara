/**
 * Domain types mirroring the Dwara backend (see docs/api-reference.md).
 * Values are exactly what the Spring Boot controllers serialize — no mocks.
 */

/** Envelope used by every app-facing endpoint. */
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

export type VehicleType = "MC" | "LMV" | "HMV";

export interface User {
  userId: string;
  name: string;
  email: string;
}

/** `GET /vehicles` returns { userId, vehicles: [...] }. */
export interface Vehicle {
  vehicleId: string;
  numberPlate: string;
  type: VehicleType;
}

export interface PricingPolicy {
  /** INR per second, per vehicle class (spec D14). */
  mcRate: number;
  lmvRate: number;
  hmvRate: number;
}

export interface ParkingFacility {
  facilityId: string;
  facilityCode: string;
  name: string;
  address: string;
  status: "ACTIVE" | "INACTIVE" | "MAINTENANCE";
  pricingPolicy: PricingPolicy;
}

export interface ParkingSession {
  sessionId: string;
  sessionStatus: SessionStatus;
  facilityCode: string;
  facilityName: string;
  vehicleId: string;
  numberPlate: string;
  /** ISO-8601 instants from the backend clock (D19). */
  createdAt: string | null;
  entryAt: string | null;
  exitAt: string | null;
  estimatedFee: number;
  /** Frozen amount after payment; null until then. */
  totalFee: number | null;
}

export interface SessionPage {
  sessions: ParkingSession[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface PaymentReceipt {
  paymentId: string;
  sessionId: string;
  amount: number;
  transactionReference: string;
  paidAt: string;
}

/** Result of a gate BLE round-trip (firmware status characteristic). */
export type GateResult = "OPENED" | "DENIED" | "FAULT" | "CLOSED";

export interface GateOutcome {
  result: GateResult;
  /** Backend or firmware deny reason, e.g. PAYMENT_REQUIRED / ACTUATOR_FAULT. */
  reason: string;
}

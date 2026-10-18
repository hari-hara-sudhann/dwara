/**
 * Mock implementation of ApiClient. Simulates the Spring Boot backend with
 * in-memory state and realistic latencies. Replace with a real REST client
 * (fetch + JWT header) once backend controllers exist.
 */

import type { ApiClient } from "./types";
import type {
  ParkingFacility,
  ParkingSession,
  PaymentMethod,
  ResponseObject,
  User,
  Vehicle,
} from "../types";

const delay = (ms: number) => new Promise<void>((r) => setTimeout(r, ms));

const ok = <T,>(data: T, message = "OK"): ResponseObject<T> => ({ success: true, message, data });
const fail = <T,>(message: string): ResponseObject<T> => ({ success: false, message, data: null as never });

class MockApi implements ApiClient {
  private user: User = {
    id: "usr_1",
    name: "Hari",
    email: "hari@dwara.me",
    phone: "+91 98765 43210",
  };
  private vehicles: Vehicle[] = [
    { id: "veh_1", licensePlate: "KA 05 MJ 4821", make: "Honda", model: "Activa 6G", color: "Black" },
    { id: "veh_2", licensePlate: "KA 01 AB 1177", make: "Hyundai", model: "i20", color: "Polar White" },
  ];
  private facilities: ParkingFacility[] = [
    {
      id: "fac_1",
      facilityCode: "DW001",
      name: "Indiranagar Metro Parking",
      address: "100 Ft Road, Indiranagar, Bengaluru",
      ratePerSecond: 0.05, // ₹180/hr flat
      totalSlots: 120,
      availableSlots: 47,
    },
    {
      id: "fac_2",
      facilityCode: "DW002",
      name: "Phoenix Marketcity",
      address: "Whitefield Road, Mahadevapura, Bengaluru",
      ratePerSecond: 0.08, // ₹288/hr flat
      totalSlots: 340,
      availableSlots: 12,
    },
  ];
  private sessions: ParkingSession[] = [
    {
      id: "ses_old_1",
      vehicleId: "veh_1",
      facilityId: "fac_1",
      status: "EXITED",
      startedAt: Date.now() - 1000 * 60 * 60 * 26,
      endedAt: Date.now() - 1000 * 60 * 60 * 24,
      fee: 118.5,
    },
  ];
  private payments: PaymentMethod[] = [
    { id: "upi", label: "UPI", detail: "hari@okicici" },
    { id: "card", label: "Card", detail: "•••• 4821" },
  ];

  async register(_body: {
    name: string;
    email: string;
    phone: string;
    password: string;
  }): Promise<ResponseObject<{ token: string; user: User }>> {
    await delay(600);
    return ok({ token: "mock-jwt", user: this.user }, "Registered");
  }

  async login(_body: { email: string; password: string }): Promise<ResponseObject<{ token: string; user: User }>> {
    await delay(600);
    return ok({ token: "mock-jwt", user: this.user }, "Logged in");
  }

  async listVehicles(): Promise<ResponseObject<Vehicle[]>> {
    await delay(250);
    return ok(this.vehicles);
  }

  async createVehicle(body: Omit<Vehicle, "id">): Promise<ResponseObject<Vehicle>> {
    await delay(450);
    if (this.vehicles.some((v) => v.licensePlate.toUpperCase() === body.licensePlate.toUpperCase())) {
      return fail("A vehicle with this plate already exists");
    }
    const vehicle: Vehicle = { ...body, id: `veh_${Date.now()}` };
    this.vehicles.push(vehicle);
    return ok(vehicle, "Vehicle added");
  }

  async deleteVehicle(id: string): Promise<ResponseObject<null>> {
    await delay(300);
    this.vehicles = this.vehicles.filter((v) => v.id !== id);
    return ok(null, "Vehicle removed");
  }

  async getFacility(facilityCode: string): Promise<ResponseObject<ParkingFacility>> {
    await delay(400);
    const facility = this.facilities.find((f) => f.facilityCode === facilityCode);
    return facility ? ok(facility) : fail(`No facility found for code ${facilityCode}`);
  }

  async createSession(body: { facilityCode: string; vehicleId: string }): Promise<ResponseObject<ParkingSession>> {
    await delay(500);
    if (this.sessions.some((s) => s.status === "PARKED" || s.status === "CREATED" || s.status === "PAYMENT_PENDING")) {
      return fail("You already have an active session (one per user, D9)");
    }
    const facility = this.facilities.find((f) => f.facilityCode === body.facilityCode);
    if (!facility) return fail("Unknown facility");
    const session: ParkingSession = {
      id: `ses_${Date.now()}`,
      vehicleId: body.vehicleId,
      facilityId: facility.id,
      status: "CREATED",
      startedAt: null,
      endedAt: null,
      fee: 0,
    };
    this.sessions.push(session);
    return ok(session, "Session created");
  }

  async getActiveSession(): Promise<ResponseObject<ParkingSession | null>> {
    await delay(250);
    const active = this.sessions.find(
      (s) => s.status === "CREATED" || s.status === "PARKED" || s.status === "PAYMENT_PENDING"
    );
    return ok(active ?? null);
  }

  async getSessionFee(sessionId: string): Promise<ResponseObject<{ fee: number }>> {
    await delay(200);
    const session = this.sessions.find((s) => s.id === sessionId);
    if (!session || !session.startedAt) return fail("Session not billable yet");
    const facility = this.facilities.find((f) => f.id === session.facilityId);
    const seconds = Math.max(1, (Date.now() - session.startedAt) / 1000);
    return ok({ fee: Math.round(seconds * (facility?.ratePerSecond ?? 0.05) * 100) / 100 });
  }

  async paySession(sessionId: string, _methodId?: string): Promise<ResponseObject<ParkingSession>> {
    await delay(900); // simulate mock payment gateway (D10)
    const session = this.sessions.find((s) => s.id === sessionId);
    if (!session) return fail("Session not found");
    const fee = (await this.getSessionFee(sessionId)).data.fee;
    session.fee = fee;
    session.status = "PAYMENT_PENDING";
    return ok(session, "Payment successful");
  }

  async cancelSession(sessionId: string): Promise<ResponseObject<ParkingSession>> {
    await delay(400);
    const session = this.sessions.find((s) => s.id === sessionId);
    if (!session) return fail("Session not found");
    session.status = "ABORTED";
    session.endedAt = Date.now();
    return ok(session, "Session cancelled");
  }

  async listHistory(): Promise<ResponseObject<ParkingSession[]>> {
    await delay(300);
    return ok(
      this.sessions
        .filter((s) => s.status === "EXITED" || s.status === "ABORTED")
        .sort((a, b) => (b.endedAt ?? 0) - (a.endedAt ?? 0))
    );
  }

  async confirmEntry(sessionId: string): Promise<ResponseObject<ParkingSession>> {
    await delay(300);
    const session = this.sessions.find((s) => s.id === sessionId);
    if (!session) return fail("Session not found");
    session.status = "PARKED";
    session.startedAt = Date.now(); // backend clock is the billing clock (D19)
    return ok(session, "Vehicle parked");
  }

  async confirmExit(sessionId: string): Promise<ResponseObject<ParkingSession>> {
    await delay(300);
    const session = this.sessions.find((s) => s.id === sessionId);
    if (!session) return fail("Session not found");
    session.status = "EXITED";
    session.endedAt = Date.now();
    return ok(session, "Session complete");
  }

  listMethods(): PaymentMethod[] {
    return this.payments;
  }
}

export const api: MockApi = new MockApi();

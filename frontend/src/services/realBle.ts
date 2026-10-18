/**
 * Real implementation of BleClient against the ESP32 firmware
 * (firmware/dwara-gate). Protocol (config.h / docs/ble-protocol.md):
 *
 *   Advertisement local name:  DWARA-<facilityCode>
 *   Service UUID:              4fafc201-1fb5-459e-8fcc-c5c9c331914b
 *   Command char (write):      beb5483e-36e1-4688-b7f5-ea07361b26a8
 *   Status char (read+notify): c7d4a2d0-5f3a-4c3e-9b1a-8e2f6d4c5b7a
 *
 * Flow per gate command: connect -> subscribe to status notify -> write the
 * JSON command -> resolve with the first matching status notification (or
 * time out). Subscribing BEFORE writing avoids losing a fast notify.
 *
 * NOTE: react-native-ble-plx needs native code — this cannot run in Expo Go.
 * Build with `npx expo prebuild` + `npx expo run:ios` / `npx expo run:android`
 * (the config plugin in app.json handles BLE permissions).
 */

import { BleManager, type Device, type Subscription } from "@sfourdrinier/react-native-ble-plx";
import { Buffer } from "buffer";
import type { BleClient } from "./types";

const SERVICE_UUID = "4fafc201-1fb5-459e-8fcc-c5c9c331914b";
const COMMAND_CHAR_UUID = "beb5483e-36e1-4688-b7f5-ea07361b26a8";
const STATUS_CHAR_UUID = "c7d4a2d0-5f3a-4c3e-9b1a-8e2f6d4c5b7a";
const NAME_PREFIX = "DWARA-";

const SCAN_TIMEOUT_MS = 8000;
const COMMAND_TIMEOUT_MS = 12000; // firmware settles the servo in <1s; generous for retries

type GateResult = "OPENED" | "DENIED" | "FAULT" | "CLOSED";

interface StatusPayload {
  sessionId: string;
  result: GateResult;
  reason: string;
}

function decodeStatus(raw: string | null): StatusPayload | null {
  if (!raw) return null;
  try {
    const json = Buffer.from(raw, "base64").toString("utf8");
    const parsed = JSON.parse(json) as StatusPayload;
    if (!parsed || typeof parsed.result !== "string") return null;
    return parsed;
  } catch {
    return null;
  }
}

class RealBle implements BleClient {
  private manager = new BleManager();
  /** facilityCode -> connected device, reused across commands. */
  private connected = new Map<string, Device>();

  async scanFacilities(scanSeconds = SCAN_TIMEOUT_MS / 1000): Promise<{ facilityCode: string; deviceName: string }[]> {
    // Drop stale connections from a previous scan round.
    await this.disconnectAll();

    const found = new Map<string, string>(); // facilityCode -> deviceName
    return new Promise((resolve) => {
      let finished = false;
      const finish = () => {
        if (finished) return;
        finished = true;
        this.manager.stopDeviceScan();
        resolve([...found].map(([facilityCode, deviceName]) => ({ facilityCode, deviceName })));
      };

      const timeout = setTimeout(finish, scanSeconds * 1000);

      this.manager.startDeviceScan([SERVICE_UUID], { allowDuplicates: false }, (error, device) => {
        if (error) {
          console.warn("[ble] scan error:", error.message);
          clearTimeout(timeout);
          finish();
          return;
        }
        const name = device?.localName ?? device?.name;
        if (!name || !name.startsWith(NAME_PREFIX)) return;

        const facilityCode = name.slice(NAME_PREFIX.length);
        if (facilityCode && !found.has(facilityCode)) {
          found.set(facilityCode, name);
        }
      });
    });
  }

  async sendGateCommand(
    facilityCode: string,
    sessionId: string,
    intent: "ENTRY" | "EXIT"
  ): Promise<"OPENED" | "DENIED" | "FAULT"> {
    const device = await this.ensureConnected(facilityCode);

    const payload = JSON.stringify({ sessionId, intent });
    const base64 = Buffer.from(payload, "utf8").toString("base64");

    const status = await this.withStatusNotification(device, sessionId, async () => {
      // discoverAllServicesAndCharacteristics is required before characteristic ops
      await device.discoverAllServicesAndCharacteristics();
      await device.writeCharacteristicWithResponseForService(
        SERVICE_UUID,
        COMMAND_CHAR_UUID,
        base64
      );
    });

    if (!status) throw new Error("Gate did not respond (timeout)");
    if (status.result === "OPENED") return "OPENED";
    if (status.result === "DENIED") return "DENIED";
    return "FAULT";
  }

  /** Extra API used by the GateTest screen: explicit close, reports CLOSED. */
  async sendGateClose(facilityCode: string, sessionId: string): Promise<"CLOSED" | "FAULT"> {
    const device = await this.ensureConnected(facilityCode);

    const payload = JSON.stringify({ sessionId, intent: "CLOSE" });
    const base64 = Buffer.from(payload, "utf8").toString("base64");

    const status = await this.withStatusNotification(device, sessionId, async () => {
      await device.discoverAllServicesAndCharacteristics();
      await device.writeCharacteristicWithResponseForService(
        SERVICE_UUID,
        COMMAND_CHAR_UUID,
        base64
      );
    });

    if (!status) throw new Error("Gate did not respond (timeout)");
    return status.result === "CLOSED" ? "CLOSED" : "FAULT";
  }

  async disconnectAll(): Promise<void> {
    for (const [code, device] of this.connected) {
      try {
        await device.cancelConnection();
      } catch {
        // device may already be gone; nothing to do
      }
      this.connected.delete(code);
    }
  }

  // -------------------------------------------------------------------------
  // private
  // -------------------------------------------------------------------------

  private async ensureConnected(facilityCode: string): Promise<Device> {
    const existing = this.connected.get(facilityCode);
    if (existing && (await existing.isConnected())) {
      return existing;
    }
    this.connected.delete(facilityCode);

    // Find the device by local name again (scan is fast; the gate is nearby
    // by construction — the user is standing in front of it).
    const devices = await this.scanForName(`${NAME_PREFIX}${facilityCode}`, 4000);
    if (devices.length === 0) {
      throw new Error(`Gate ${facilityCode} not found — move closer and retry`);
    }

    const device = await this.manager.connectToDevice(devices[0].id, { requestMTU: 185 });
    device.onDisconnected(() => {
      this.connected.delete(facilityCode);
    });
    this.connected.set(facilityCode, device);
    return device;
  }

  private scanForName(name: string, timeoutMs: number): Promise<Device[]> {
    const matches: Device[] = [];
    return new Promise((resolve) => {
      let finished = false;
      const finish = () => {
        if (finished) return;
        finished = true;
        this.manager.stopDeviceScan();
        resolve(matches);
      };
      const timeout = setTimeout(finish, timeoutMs);

      this.manager.startDeviceScan([SERVICE_UUID], { allowDuplicates: false }, (error, device) => {
        if (error) {
          clearTimeout(timeout);
          finish();
          return;
        }
        const dname = device?.localName ?? device?.name;
        if (dname === name) {
          matches.push(device!);
          clearTimeout(timeout);
          finish();
        }
      });
    });
  }

  /**
   * Subscribe to the status characteristic, run the write, and resolve with
   * the first notification whose sessionId matches (or null on timeout).
   */
  private async withStatusNotification(
    device: Device,
    sessionId: string,
    write: () => Promise<void>
  ): Promise<StatusPayload | null> {
    return new Promise<StatusPayload | null>((resolve) => {
      let sub: Subscription | null = null;
      let timeout: ReturnType<typeof setTimeout> | null = null;
      let settled = false;

      const done = (value: StatusPayload | null) => {
        if (settled) return;
        settled = true;
        if (timeout) clearTimeout(timeout);
        sub?.remove();
        resolve(value);
      };

      timeout = setTimeout(() => done(null), COMMAND_TIMEOUT_MS);

      try {
        sub = device.monitorCharacteristicForService(SERVICE_UUID, STATUS_CHAR_UUID, (error, characteristic) => {
          if (error || !characteristic) {
            done(null);
            return;
          }
          const status = decodeStatus(characteristic.value);
          if (status && status.sessionId === sessionId) {
            done(status);
          }
          // Non-matching payloads (e.g. an earlier command's report) are ignored.
        });
        // Subscription is live — now perform the write.
        void write().catch((e: Error) => {
          console.warn("[ble] command failed:", e?.message ?? e);
          done(null);
        });
      } catch (e) {
        console.warn("[ble] monitor setup failed:", (e as Error)?.message ?? e);
        done(null);
      }
    });
  }
}

export const realBle: RealBle = new RealBle();

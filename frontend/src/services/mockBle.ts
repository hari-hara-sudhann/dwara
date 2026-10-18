/**
 * Mock implementation of BleClient. Simulates scanning for `DWARA-<facilityCode>`
 * advertisements and the GATT round-trip with the ESP32 (command + status
 * characteristics). Swap for a react-native-ble-plx implementation later.
 *
 * Real protocol (firmware/dwara-gate/config.h):
 *   Service UUID:         4fafc201-1fb5-459e-8fcc-c5c9c331914b
 *   Command char (write): beb5483e-36e1-4688-b7f5-ea07361b26a8
 *   Status char (notify): c7d4a2d0-5f3a-4c3e-9b1a-8e2f6d4c5b7a
 */

import type { BleClient } from "./types";

const delay = (ms: number) => new Promise<void>((r) => setTimeout(r, ms));

const KNOWN_FACILITIES = [
  { facilityCode: "DW001", deviceName: "DWARA-DW001" },
  { facilityCode: "DW002", deviceName: "DWARA-DW002" },
];

class MockBle implements BleClient {
  async scanFacilities(scanSeconds = 2500) {
    await delay(scanSeconds);
    // Pretend only DW001 is in range right now.
    return KNOWN_FACILITIES.slice(0, 1);
  }

  async sendGateCommand(_facilityCode: string, _sessionId: string, _intent: "ENTRY" | "EXIT") {
    await delay(1200); // firmware opens over ~8s fixed timer (D17); notify comes earlier
    return "OPENED" as const;
  }
}

export const ble: BleClient = new MockBle();

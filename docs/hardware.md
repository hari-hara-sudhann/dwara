# Hardware

The gate is a single ESP32 dev board driving one MG90S hobby servo with the
gate arm. Everything below is what the firmware in `firmware/dwara-gate`
expects.

## Bill of materials

| Part | Qty | Notes |
|---|---|---|
| ESP32 20-pin dev board | 1 | Any ESP32-WROOM devkit with 3.3 V logic |
| MG90S micro servo | 1 | Metal-gear; ~4.8–6 V |
| External 5 V supply (≥1 A) | 1 | Separate from the ESP32's 3V3 rail |
| 470–1000 µF electrolytic capacitor | 1 | Across the servo's 5 V/GND, near the servo |
| Gate arm + hinge | 1 | Light cardboard/acrylic arm for the demo |
| Jumper wires, breadboard | — | |
| USB cable | 1 | Flashing + serial monitor |

## Wiring

| Signal | ESP32 pin | Connects to |
|---|---|---|
| Servo PWM | GPIO27 (D27) | MG90S **signal** (orange/yellow) |
| Ground | GND | Servo GND **and** external supply GND (common ground) |
| Servo power | — | External **5 V** (red), never the ESP32 3V3 pin |
| Network mode jumper | GPIO26 (D26) | Leave open for WiFi; jumper to GND for the serial bridge |
| Status LED (optional) | GPIO2 | On-board LED, activity indication only |

```
        ESP32                       MG90S
     ┌─────────┐                 ┌─────────┐
     │  GPIO27 ├──── signal ────┤ orange  │
     │  GND    ├────┬───────────┤ brown   │
     └─────────┘    │           │ red     ├──── +5 V external
                   GND          └─────────┘
                    │                │
                   === 470 µF        │
                    │                │
                   GND ──────────────┘
```

**Never power the servo from the ESP32's 3V3 rail.** The MG90S stall current
browns out the board: the classic symptom is the ESP32 resetting (or the BLE
connection dropping) the instant the gate moves. Use a separate 5 V supply with
a common ground and the decoupling capacitor.

GPIO26 is `INPUT_PULLUP` at boot, so leaving it unconnected selects WiFi. Do not
move it to GPIO34–39 — those are input-only and have no internal pull-up.

## Mechanical

- Mount the servo so `0°` is **gate closed** and `90°` is **gate open**
  (`CLOSED_ANGLE` / `OPEN_ANGLE` in `config.h`).
- Keep the arm light; the servo has no position feedback, so weight and friction
  are what make a sweep overrun its settle window.
- Add a **push-through override** (a sprung hinge or a loose pin) so a person can
  always open the gate by hand if the electronics fail. This is a safety
  requirement, not a convenience.
- There are no entry/exit sensors (spec D8/D15): the gate closes on a fixed
  8-second timer.

## Actuator fault behavior

The firmware has no servo position feedback, so it cannot detect a stalled arm
directly. It bounds every movement with `ACTUATOR_SETTLE_TIMEOUT_MS` (3 s):
if the movement has not completed, it drives the servo back to `CLOSED`,
releases the PWM signal (`detach()`), latches `FAULT`, and reports
`DENIED/ACTUATOR_FAULT` to the app. A latched fault requires a reset.

To exercise that path on the bench, set `DWARA_FORCE_ACTUATOR_FAULT` to `1` in
`config.h`, flash, send an `ENTRY`, and watch for:

```
[actuator] FAULT: settle timeout -> forcing CLOSED
[gate] outcome=FAULT reason=ACTUATOR_FAULT
```

Then set it back to `0`. With the real servo, block the arm with your hand
during a sweep — the same watchdog applies if the sweep cannot finish, and the
servo stops being driven.

## Bench / demo checklist

1. Backend up (`docker compose up -d --build`), demo facility `FAC-0001` seeded.
2. Register the gate (`POST /devices`, admin key) and put `controllerId` and
   `deviceSecret` into `config.h`, along with `FACILITY_CODE`, `DEVICE_ID`,
   `DEVICE_SECRET`, `WIFI_SSID`, `WIFI_PASSWORD`, `BACKEND_BASE_URL`.
3. Flash with **Huge APP (3 MB No OTA / 1 MB SPIFFS)**.
4. Serial monitor at 115200: expect `[net] GPIO26 HIGH -> WiFi`, `[actuator]
   servo on GPIO27 attached`, `[ble] advertising as DWARA-FAC-0001`.
5. Scan from the app, start a session, tap **Open Entry Gate**: servo opens,
   `[gate] authorized -> opening`, then closes after 8 s.
6. Pay, then **Open Exit Gate**: opens again and the session becomes `EXITED`.

If the servo cannot be powered reliably during the demo, the LED stand-in
(`LedGateActuator`) can be swapped in with one line in `dwara-gate.ino`; see
[docs/firmware.md](firmware.md).

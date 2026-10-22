# Mobile App (Expo / React Native)

Location: `frontend/`. The app talks to the real backend over REST and to the
gate over BLE. **There are no mock API or mock BLE implementations** — a failed
request shows the backend's message instead of falling back to fake data.

## Setup

```bash
cd frontend
npm install
npx expo prebuild          # generates ios/ and android/ native projects
npx expo run:ios           # or: npx expo run:android
```

`react-native-ble-plx` contains native code, so **Expo Go cannot run this app**.
Use a development build / `expo run:*` with a physical device.

### Backend address

The client reads `EXPO_PUBLIC_API_URL` (default `http://localhost:8080`):

```bash
EXPO_PUBLIC_API_URL=http://192.168.1.20:8080 npx expo run:ios
```

- iOS simulator can use `localhost`.
- A physical iPhone must use the laptop's LAN IP; the phone and laptop must be
  on the same network, and the backend port (`APP_PORT`, default `8080`) must be
  reachable (macOS may prompt to allow incoming connections).
- Use `https://` and a verified certificate outside local development.

Permissions are declared in `app.json` (Bluetooth + `NSBluetoothAlwaysUsageDescription`
through the BLE config plugin). iOS shows a Bluetooth permission prompt the
first time the app scans.

## Screens

| Screen | Backend / BLE |
|---|---|
| Welcome | static onboarding |
| Register | `POST /auth/register` then `POST /auth/login` |
| Login | `POST /auth/login` then `GET /auth/me` |
| Dashboard | active session + vehicles from context (`GET /sessions/active`, `GET /vehicles`) |
| Discover | `realBle.scanFacilities()` → `GET /facilities/{facilityCode}` |
| Start Session | `POST /sessions` → `GET /sessions/active` |
| Active Session | BLE `ENTRY`/`EXIT`; `GET /sessions/{id}/fee` while parked |
| Payment | `GET /sessions/{id}/fee` (2 s poll) → `POST /sessions/{id}/pay` |
| Activity | `GET /sessions?page=0&size=50` |
| Profile | `POST/GET/DELETE /vehicles`; Gate Test bench screen |
| Gate Test | BLE scan + `ENTRY`/`CLOSE` for bench work |

## Auth and state

- The JWT is stored in `AsyncStorage` (`dwara.jwt`) by `src/services/api.ts`.
- On launch `AppContext` loads the token, calls `GET /auth/me`, and holds the
  splash until it resolves. Killing and relaunching keeps the user signed in.
- Any `401`/`403` clears the token and returns to the login screen with a
  message.
- Business refusals (HTTP 200, `success: false`) leave the user signed in and
  show `message` in an alert or inline text.

## Entry / exit over BLE

1. The app connects to the peripheral advertising `DWARA-<facilityCode>`.
2. It subscribes to the status characteristic, then writes
   `{"sessionId":"<uuid>","intent":"ENTRY"|"EXIT"}` to the command
   characteristic (see [ble-protocol.md](ble-protocol.md)).
3. The firmware calls `POST /gate/validate` with its device secret and moves the
   servo only if the backend says `open`.
4. The firmware notifies `{"sessionId","result","reason"}`; the app shows the
   matching reason (`PAYMENT_REQUIRED`, `BACKEND_UNREACHABLE`, `ACTUATOR_FAULT`,
   …) and refreshes the session from the backend.

The app never decides whether a gate may open. The BLE round-trip times out
after 15 s and reports a Bluetooth error; the gate may already have acted, so
the screen re-reads the session state afterwards.

## iOS caveats (v1 scope)

- Foreground scanning only; no background BLE.
- Discovery requires the phone within a few metres of the gate.
- Simulators have no Bluetooth radio: use a physical iPhone for the BLE steps.
  REST-only screens work in the simulator.

## Type check

```bash
npm --prefix frontend run typecheck
```

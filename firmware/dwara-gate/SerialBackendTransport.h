/**
 * ============================================================================
 *  DWARA — SerialBackendTransport (DEMO ONLY, not production)
 * ============================================================================
 *  Selected when NETWORK_MODE_PIN is LOW (the pin is grounded). Instead of
 *  opening a socket itself, the ESP32 writes one framed request line to the
 *  USB serial monitor:
 *
 *    DWRAREQ {"id":7,"method":"POST","path":"/gate/validate",
 *             "authorization":"Bearer <secret>","body":"{...}"}
 *
 *  tools/serial-bridge/bridge.py parses that line, performs the same HTTP call
 *  against the locally running Spring Boot app, and answers:
 *
 *    DWARERES {"id":7,"status":200,"body":"{\"decision\":\"open\",...}"}
 *
 *  Frames are prefixed and newline-terminated so ordinary [gate]/[auth] log
 *  lines can flow in both directions without confusing either side. The bridge
 *  is a bench aid for the demo; WiFi remains the primary path.
 * ============================================================================
 */

#pragma once

#include <Arduino.h>
#include <ArduinoJson.h>

#include "BackendTransport.h"
#include "config.h"

class SerialBackendTransport final : public BackendTransport {
 public:
  explicit SerialBackendTransport(Stream &serial) : _serial(serial) {}

  bool begin() override {
    // Serial itself is owned by setup(); nothing to bring up here.
    Serial.println("[net:serial] bridge mode — the host bridge forwards "
                   "requests to the backend");
    return true;
  }

  const char *name() const override { return "serial"; }

  BackendResponse send(const BackendRequest &request) override {
    BackendResponse out;
    const uint32_t id = ++_nextId;

    JsonDocument frame;
    frame["id"] = id;
    frame["method"] = request.method;
    frame["path"] = request.path;
    if (request.authorization != nullptr && request.authorization[0] != '\0') {
      frame["authorization"] = request.authorization;
    }
    if (request.body != nullptr && request.body[0] != '\0') {
      frame["body"] = request.body;
    }

    _serial.print(SERIAL_BRIDGE_REQ_PREFIX);
    serializeJson(frame, _serial);
    _serial.print('\n');
    _serial.flush();

    // Wait for the matching response. Any line that is not a DWARERES frame
    // (our own logs, boot noise, a bridge restart) is ignored.
    const uint32_t deadline = millis() + SERIAL_BRIDGE_TIMEOUT_MS;
    while ((int32_t)(millis() - deadline) < 0) {
      if (_serial.available() <= 0) {
        delay(2);
        continue;
      }

      String line = _serial.readStringUntil('\n');
      line.trim();
      if (!line.startsWith(SERIAL_BRIDGE_RES_PREFIX)) {
        continue;
      }

      JsonDocument response;
      const String payload = line.substring(strlen(SERIAL_BRIDGE_RES_PREFIX));
      if (deserializeJson(response, payload) != DeserializationError::Ok) {
        continue;
      }
      if ((response["id"] | 0UL) != id) {
        continue;  // stale answer to an earlier request
      }

      out.status = response["status"] | 0;
      out.body = response["body"] | "";
      return out;
    }

    Serial.printf("[net:serial] bridge timed out after %lums (is "
                  "tools/serial-bridge running?)\n",
                  (unsigned long)SERIAL_BRIDGE_TIMEOUT_MS);
    return out;
  }

 private:
  Stream &_serial;
  uint32_t _nextId = 0;
};

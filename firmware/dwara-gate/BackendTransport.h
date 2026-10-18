/**
 * ============================================================================
 *  DWARA — BackendTransport (abstract request/response link)
 * ============================================================================
 *  The gate firmware only cares that some link can carry one HTTP-equivalent
 *  request to the backend and return the status + body. Which link does it is
 *  a boot-time decision in dwara-gate.ino:
 *
 *    - WiFiBackendTransport    — production. WiFi station + HTTPClient.
 *    - SerialBackendTransport  — demo only. Frames go over the USB serial
 *                                monitor to tools/serial-bridge, which performs
 *                                the actual HTTP call against the same backend.
 *
 *  Both implementations share this interface so BackendClient (and therefore
 *  the gate flow) is identical in either mode.
 * ============================================================================
 */

#pragma once

#include <Arduino.h>

struct BackendRequest {
  const char *method;         // "GET" or "POST"
  const char *path;           // e.g. "/gate/validate" (appended to base URL)
  const char *authorization;  // full header value, or nullptr
  const char *body;           // JSON payload, or nullptr for GET
};

struct BackendResponse {
  int status = 0;  // HTTP status code; 0 means the transport could not deliver
  String body;
};

class BackendTransport {
 public:
  // Bring the link up. Returns false when the link is unavailable; requests
  // will then fail closed with status 0.
  virtual bool begin() = 0;

  // Perform a single blocking request. Implementations must give up within
  // their configured timeout and return a status of 0 on failure.
  virtual BackendResponse send(const BackendRequest &request) = 0;

  // Short human-readable name used in boot logs ("wifi" / "serial").
  virtual const char *name() const = 0;

  virtual ~BackendTransport() = default;
};

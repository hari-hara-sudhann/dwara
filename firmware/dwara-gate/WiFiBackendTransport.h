/**
 * ============================================================================
 *  DWARA — WiFiBackendTransport (production link)
 * ============================================================================
 *  Station-mode WiFi + HTTPClient. Selected when NETWORK_MODE_PIN is HIGH
 *  (the internal pull-up wins, i.e. the pin is not grounded).
 * ============================================================================
 */

#pragma once

#include <Arduino.h>
#include <HTTPClient.h>
#include <WiFi.h>

#include "BackendTransport.h"
#include "config.h"

class WiFiBackendTransport final : public BackendTransport {
 public:
  bool begin() override {
    if (WiFi.status() == WL_CONNECTED) {
      return true;
    }
    WiFi.mode(WIFI_STA);
    WiFi.setAutoReconnect(true);
    WiFi.begin(WIFI_SSID, WIFI_PASSWORD);
    return ensureWifi(WIFI_CONNECT_TIMEOUT_MS);
  }

  const char *name() const override { return "wifi"; }

  BackendResponse send(const BackendRequest &request) override {
    BackendResponse out;
    // A request may not block the BLE loop longer than its own timeout, even
    // if the station is still trying to associate.
    if (!ensureWifi(BACKEND_TIMEOUT_MS)) {
      Serial.println("[net:wifi] not connected — request not sent");
      return out;
    }

    char url[192];
    snprintf(url, sizeof(url), "%s%s", BACKEND_BASE_URL, request.path);

    HTTPClient http;
    if (!http.begin(url)) {
      Serial.printf("[net:wifi] cannot open %s\n", url);
      return out;
    }
    http.setTimeout(BACKEND_TIMEOUT_MS);
    http.setConnectTimeout(BACKEND_TIMEOUT_MS);
    http.addHeader("Accept", "application/json");
    if (request.authorization != nullptr && request.authorization[0] != '\0') {
      http.addHeader("Authorization", request.authorization);
    }
    if (request.body != nullptr && request.body[0] != '\0') {
      http.addHeader("Content-Type", "application/json");
    }

    int code;
    if (request.body != nullptr && request.body[0] != '\0') {
      code = http.sendRequest(request.method, (uint8_t *)request.body,
                              strlen(request.body));
    } else {
      code = http.sendRequest(request.method);
    }

    if (code <= 0) {
      Serial.printf("[net:wifi] %s %s failed: %d\n", request.method,
                    request.path, code);
      http.end();
      return out;
    }

    out.status = code;
    out.body = http.getString();
    http.end();
    return out;
  }

 private:
  bool ensureWifi(uint32_t timeoutMs) {
    if (WiFi.status() == WL_CONNECTED) {
      return true;
    }
    const wl_status_t status = WiFi.status();
    if (status == WL_NO_SSID_AVAIL || status == WL_CONNECT_FAILED) {
      // Association failed; kick off another attempt before waiting.
      WiFi.begin(WIFI_SSID, WIFI_PASSWORD);
    }
    const uint32_t deadline = millis() + timeoutMs;
    while (WiFi.status() != WL_CONNECTED && millis() < deadline) {
      delay(100);
    }
    return WiFi.status() == WL_CONNECTED;
  }
};

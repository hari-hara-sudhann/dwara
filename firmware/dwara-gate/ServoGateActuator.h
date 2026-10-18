/**
 * ============================================================================
 *  DWARA — Servo gate actuator (MG90S)
 * ============================================================================
 *  Drives the MG90S servo signal line (config ACTUATOR_PIN, currently D27 /
 *  GPIO27). open() sweeps to OPEN_ANGLE, close() back to CLOSED_ANGLE — the
 *  mechanics stay abstract behind GateActuator (spec D15).
 *
 *  Non-blocking: writes the target angle immediately (the servo library and
 *  the hardware take care of the physical sweep), then runs a settle window.
 *  The gate is only "settled" — and thus reported OPENED — once the window
 *  elapses, so the app-visible outcome still means "gate is in position".
 *
 *  Uses the ESP32Servo library (Arduino Library Manager: "ESP32Servo").
 * ============================================================================
 */

#pragma once

#include <Arduino.h>
#include <ESP32Servo.h>
#include "GateActuator.h"
#include "config.h"

class ServoGateActuator final : public GateActuator {
 public:
  ServoGateActuator(uint8_t servoPin, uint8_t openAngle, uint8_t closedAngle);

  void begin() override;   // attach servo, force CLOSED (fail-closed boot)
  bool open() override;    // command OPEN_ANGLE
  bool close() override;   // command CLOSED_ANGLE
  void tick() override;    // settle/fault timers (non-blocking)
  bool isBusy() const override;
  bool hasSettled() const override;
  bool hasFaulted() const override;

 private:
  enum class Phase : uint8_t {
    IDLE_CLOSED,
    MOVING,       // sweep in progress (either direction)
    OPEN_IDLE,    // resting at OPEN_ANGLE
    FAULT,
  };

  bool commandAngle(uint8_t angle, Phase endPhase);

  Servo _servo;
  uint8_t _pin;
  uint8_t _openAngle;
  uint8_t _closedAngle;

  Phase _phase;
  Phase _pendingEndPhase;   // phase to enter once the settle window elapses
  uint32_t _moveStartedAt;  // millis() at the last commanded move
  bool _settled;
  bool _faulted;
};

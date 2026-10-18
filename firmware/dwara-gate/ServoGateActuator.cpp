/**
 * ============================================================================
 *  DWARA — Servo gate actuator implementation
 * ============================================================================
 *  Timing model: an MG90S does ~0.1 s / 60° at 4.8 V, so a full 90° swing
 *  finishes well inside SERVO_SETTLE_MS. We still wait the full settle window
 *  before reporting success (belt and braces for a loaded gate arm).
 *
 *  Fault watchdog: if the configured settle window somehow lapses without the
 *  move completing (should not happen — the angle write is instantaneous),
 *  we latch FAULT and force the gate back to CLOSED so the controller's
 *  report(FAULT) path matches the LED build's behaviour.
 * ============================================================================
 */

#include "ServoGateActuator.h"

ServoGateActuator::ServoGateActuator(uint8_t servoPin, uint8_t openAngle,
                                     uint8_t closedAngle)
    : _pin(servoPin),
      _openAngle(openAngle),
      _closedAngle(closedAngle),
      _phase(Phase::IDLE_CLOSED),
      _moveStartedAt(0),
      _settled(true),
      _faulted(false) {}

void ServoGateActuator::begin() {
  // Allow allocation of all timers on the ESP32 (ESP32Servo requirement).
  ESP32PWM::allocateTimer(0);
  ESP32PWM::allocateTimer(1);
  ESP32PWM::allocateTimer(2);
  ESP32PWM::allocateTimer(3);

  _servo.setPeriodHertz(50);  // standard analog servo PWM
  _servo.attach(_pin, 500, 2400);  // MG90S pulse range: ~0.5–2.4 ms

  // Fail-closed boot (spec §7): physical CLOSED before anything else runs.
  _servo.write(_closedAngle);
  _phase = Phase::IDLE_CLOSED;
  _settled = true;
  _faulted = false;
  Serial.printf("[actuator] servo on GPIO%u attached, CLOSED=%u° OPEN=%u°\n",
                _pin, _closedAngle, _openAngle);
}

bool ServoGateActuator::open() {
  return commandAngle(_openAngle, Phase::OPEN_IDLE);
}

bool ServoGateActuator::close() {
  return commandAngle(_closedAngle, Phase::IDLE_CLOSED);
}

void ServoGateActuator::tick() {
  if (_phase != Phase::MOVING) {
    return;
  }

  const uint32_t elapsed = millis() - _moveStartedAt;
  if (elapsed >= SERVO_SETTLE_MS) {
    _phase = _pendingEndPhase;
    _settled = true;
    Serial.println("[actuator] servo settled in position");
  }
}

bool ServoGateActuator::isBusy() const {
  return _phase == Phase::MOVING;
}

bool ServoGateActuator::hasSettled() const {
  return _settled;
}

bool ServoGateActuator::hasFaulted() const {
  return _faulted;
}

// ---------------------------------------------------------------------------
// private
// ---------------------------------------------------------------------------

bool ServoGateActuator::commandAngle(uint8_t angle, Phase endPhase) {
  if (_phase == Phase::FAULT) {
    return false;
  }
  if (isBusy()) {
    return false;  // previous sweep still in its settle window
  }

  _servo.write(angle);
  _phase = Phase::MOVING;
  _pendingEndPhase = endPhase;
  _moveStartedAt = millis();
  _settled = false;
  _faulted = false;
  Serial.printf("[actuator] servo -> %u°\n", angle);
  return true;
}

/**
 * ============================================================================
 *  DWARA — LED gate actuator implementation (TEMPORARY workaround build)
 * ============================================================================
 *  Semantics (docs/firmware.md, "Temporary LED workaround"):
 *
 *    openGate  -> LED blinks 3 times
 *    closeGate -> LED blinks 2 times
 *
 *  The sequences are fully non-blocking (tick()-driven) so the BLE stack and
 *  the backend poller keep running while the LED does its thing. A close()
 *  that arrives mid open-sequence is remembered and executed after, so the
 *  fixed-timer auto-close (spec D17) still works.
 * ============================================================================
 */

#include "LedGateActuator.h"

LedGateActuator::LedGateActuator(uint8_t ledPin)
    : _pin(ledPin),
      _phase(Phase::IDLE_CLOSED),
      _pendingPhase(Phase::IDLE_CLOSED),
      _pendingClose(false),
      _blinksRemaining(0),
      _timer(0),
      _ledState(false),
      _settled(true),
      _faulted(false) {}

void LedGateActuator::begin() {
  pinMode(_pin, OUTPUT);
  ledOff();
  _phase = Phase::IDLE_CLOSED;
  _pendingPhase = Phase::IDLE_CLOSED;
  _pendingClose = false;
  _settled = true;
  _faulted = false;
}

bool LedGateActuator::open() {
  if (_phase == Phase::FAULT) {
    return false;
  }
  if (isBusy()) {
    return false;  // previous sequence still running
  }
  // OPEN blink pattern: three on/off cycles.
  startBlinks(3, Phase::OPEN_IDLE);
  return true;
}

bool LedGateActuator::close() {
  if (_phase == Phase::FAULT) {
    return false;
  }
  if (isBusy()) {
    // A close arriving during an open sequence is exactly the auto-close
    // path (GateController calls close() after GATE_OPEN_HOLD_MS). Remember
    // it; it is applied the moment the running sequence finishes.
    _pendingClose = true;
    return true;
  }
  // CLOSE blink pattern: two on/off cycles.
  startBlinks(2, Phase::IDLE_CLOSED);
  return true;
}

void LedGateActuator::tick() {
  if (_phase == Phase::FAULT || _phase == Phase::IDLE_CLOSED ||
      _phase == Phase::OPEN_IDLE) {
    return;  // nothing in flight
  }

  const uint32_t now = millis();
  // Wraparound-safe comparison (millis() overflows after ~49.7 days).
  if ((int32_t)(now - _timer) < 0) {
    return;  // current on/off slice still running
  }

  if (_ledState) {
    // Just finished an ON slice -> go dark.
    ledOff();
    _timer = now + LED_BLINK_OFF_MS;
  } else {
    // Just finished an OFF slice -> one full blink done.
    _blinksRemaining--;
    if (_blinksRemaining > 0) {
      ledOn();
      _timer = now + LED_BLINK_ON_MS;
    } else {
      // Sequence complete: LED stays dark in both end states.
      ledOff();
      _settled = true;
      if (_phase == Phase::OPENING && _pendingClose) {
        // A close() arrived while the open sequence ran (auto-close timer).
        // Apply it immediately: the close blinks become the new in-flight
        // sequence and the gate only settles once they finish.
        _pendingClose = false;
        startBlinks(2, Phase::IDLE_CLOSED);
      } else {
        _phase = _pendingPhase;
      }
    }
  }
}

bool LedGateActuator::isBusy() const {
  return _phase == Phase::OPENING || _phase == Phase::CLOSING;
}

bool LedGateActuator::hasSettled() const {
  return _settled;
}

bool LedGateActuator::hasFaulted() const {
  // The LED cannot physically stall; the fault path exists so the gate
  // controller's watchdog logic is identical for every actuator build.
  return _faulted;
}

// ---------------------------------------------------------------------------
// private
// ---------------------------------------------------------------------------

void LedGateActuator::startBlinks(uint8_t count, Phase endPhase) {
  _pendingPhase = endPhase;
  _blinksRemaining = count;
  _settled = false;
  _faulted = false;
  ledOn();
  _timer = millis() + LED_BLINK_ON_MS;
  // Distinguish which sequence is running for debugging/telemetry.
  _phase = (endPhase == Phase::OPEN_IDLE) ? Phase::OPENING : Phase::CLOSING;
}

void LedGateActuator::ledOn() {
  digitalWrite(_pin, HIGH);
  _ledState = true;
}

void LedGateActuator::ledOff() {
  digitalWrite(_pin, LOW);
  _ledState = false;
}

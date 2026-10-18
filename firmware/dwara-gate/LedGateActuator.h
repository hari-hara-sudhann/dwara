/**
 * ============================================================================
 *  DWARA — LED gate actuator (TEMPORARY workaround build)
 * ============================================================================
 *  The MG90S servo currently has a power-supply problem (brownout/reset when
 *  the motor starts under load). Until that is fixed, the physical gate
 *  action is STOOD IN FOR by an LED on pin D18:
 *
 *    openGate  -> LED blinks 3 times
 *    closeGate -> LED blinks 2 times
 *
 *  This is a pure software swap behind GateActuator; no other layer changes.
 *  Context and revert path: docs/firmware.md, section "Temporary LED
 *  workaround (servo power issue)".
 * ============================================================================
 */

#pragma once

#include <Arduino.h>
#include "GateActuator.h"
#include "config.h"

class LedGateActuator final : public GateActuator {
 public:
  explicit LedGateActuator(uint8_t ledPin);

  void begin() override;      // pinMode OUTPUT, LED off, state = closed
  bool open() override;       // start 3-blink sequence
  bool close() override;      // start 2-blink sequence
  void tick() override;       // drive blink timing (non-blocking)
  bool isBusy() const override;
  bool hasSettled() const override;
  bool hasFaulted() const override;

 private:
  enum class Phase : uint8_t {
    IDLE_CLOSED,   // commanded closed, sequence finished
    OPENING,       // running the blink sequence for an OPEN command
    CLOSING,       // running the blink sequence for a CLOSE command
    OPEN_IDLE,     // sequence done, gate logically OPEN (until close())
    FAULT,
  };

  void startBlinks(uint8_t count, Phase phase);
  void ledOn();
  void ledOff();

  uint8_t _pin;
  Phase _phase;
  Phase _pendingPhase;   // phase to enter after the blink sequence completes
  bool _pendingClose;    // a close() arrived while an open sequence ran

  uint8_t _blinksRemaining;
  uint32_t _timer;       // millis() deadline for the current on/off slice
  bool _ledState;
  bool _settled;
  bool _faulted;
};

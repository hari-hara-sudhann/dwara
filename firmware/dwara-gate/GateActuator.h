/**
 * ============================================================================
 *  DWARA — Abstract gate actuator interface (spec D15)
 * ============================================================================
 *  "Servo rotates to OPEN angle / CLOSED angle" — the physical mechanism is
 *  deliberately abstract. The gate controller firmware only ever talks to
 *  this interface, so swapping the MG90S servo build for the temporary LED
 *  build (or a future boom barrier driver) touches nothing else.
 * ============================================================================
 */

#pragma once

#include <Arduino.h>
#include "GateTypes.h"

class GateActuator {
 public:
  // Lifecycle: initialize pins/peripherals and force the gate to its CLOSED
  // position (fail-closed boot, spec §7). Must be called once from setup().
  virtual void begin() = 0;

  // Non-blocking command to move to the OPEN position. Returns false if the
  // actuator is busy with a previous movement.
  virtual bool open() = 0;

  // Non-blocking command to move to the CLOSED position. Returns false if the
  // actuator is busy with a previous movement.
  virtual bool close() = 0;

  // Tick the actuator's internal state machine (blink sequences, servo
  // sweeps, settle timers, stall watchdog). Must be called from loop() as
  // often as possible.
  virtual void tick() = 0;

  // True while a commanded movement/sequence is still in progress.
  virtual bool isBusy() const = 0;

  // True once the actuator reached its commanded position (or, for LED builds,
  // finished the blink pattern). Reset by the next open()/close().
  virtual bool hasSettled() const = 0;

  // True if the actuator failed to reach the commanded position within
  // ACTUATOR_SETTLE_TIMEOUT_MS (spec §10 edge case 12: stall/jam watchdog).
  virtual bool hasFaulted() const = 0;

  virtual ~GateActuator() = default;
};

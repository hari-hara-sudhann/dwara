package me.hari.dwara.services;

/**
 * Raised when the ESP32 calls a device endpoint without the secret that was
 * flashed at provisioning time (spec D6/D7/D11).
 */
public class InvalidDeviceCredentialsException extends RuntimeException {

    public InvalidDeviceCredentialsException() {
        super("Invalid device credentials.");
    }
}

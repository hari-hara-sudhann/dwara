package me.hari.dwara.client;

public interface GateControllerClient {
    boolean openGate();
    boolean closeGate();
    boolean isAvailable();
}

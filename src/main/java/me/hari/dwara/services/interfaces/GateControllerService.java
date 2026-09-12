package me.hari.dwara.services.interfaces;

import me.hari.dwara.dtos.ResponseObject;

public interface GateControllerService {
    ResponseObject<Void> openEntryGate();
    ResponseObject<Void> openExitGate();

    // to be done: in impl authorise entry, exit
}

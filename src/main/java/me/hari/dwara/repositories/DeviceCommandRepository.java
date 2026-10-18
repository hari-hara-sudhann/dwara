package me.hari.dwara.repositories;

import me.hari.dwara.entities.DeviceCommand;
import me.hari.dwara.entities.enums.CommandStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface DeviceCommandRepository extends JpaRepository<DeviceCommand, UUID> {
    // Oldest undelivered command first, so repeated queued opens cannot reorder.
    Optional<DeviceCommand> findFirstByDevice_ControllerIdAndStatusOrderByCreatedAtAsc(
            UUID controllerId, CommandStatus status);
}

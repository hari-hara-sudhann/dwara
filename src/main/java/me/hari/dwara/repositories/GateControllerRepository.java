package me.hari.dwara.repositories;

import me.hari.dwara.entities.GateController;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface GateControllerRepository extends JpaRepository<GateController, UUID> {
}

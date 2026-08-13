package me.hari.dwara.repositories;

import me.hari.dwara.entities.ParkingFacility;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ParkingFacilityRepository extends JpaRepository<ParkingFacility, UUID> {
    boolean existsByFacilityCode(String code);

    Optional<ParkingFacility> findByFacilityCode(String facilityCode);

    void deleteByFacilityCode(String facilityCode);
}

package me.hari.dwara.bootstrap;

import lombok.RequiredArgsConstructor;
import me.hari.dwara.entities.ParkingFacility;
import me.hari.dwara.entities.enums.ParkingFacilityStatus;
import me.hari.dwara.entities.records.PricingPolicy;
import me.hari.dwara.repositories.ParkingFacilityRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * One ACTIVE demo facility so the app has somewhere to park before an operator
 * creates facilities through {@code POST /facilities}.
 *
 * <p>Rates are per second (spec D14): ₹18/hr MC, ₹36/hr LMV, ₹72/hr HMV.
 * Disable with {@code DWARA_SEED_DEMO_FACILITY=false}; the seeder never touches
 * an existing facility.
 */
@Component
@RequiredArgsConstructor
public class DemoDataSeeder implements ApplicationRunner {

    private static final Logger LOG = LoggerFactory.getLogger(DemoDataSeeder.class);

    private final ParkingFacilityRepository facilityRepository;

    @Value("${dwara.seed-demo-facility:true}")
    private boolean enabled;

    @Value("${dwara.demo-facility-code:FAC-0001}")
    private String facilityCode;

    @Override
    public void run(ApplicationArguments args) {
        if (!enabled) {
            return;
        }
        if (facilityRepository.existsByFacilityCode(facilityCode)) {
            return;
        }

        ParkingFacility facility = new ParkingFacility();
        facility.setFacilityCode(facilityCode);
        facility.setName("Dwara Demo Parking");
        facility.setAddress("MG Road, Bengaluru");
        facility.setStatus(ParkingFacilityStatus.ACTIVE);
        facility.setPricingPolicy(new PricingPolicy(
                new BigDecimal("0.005"),
                new BigDecimal("0.010"),
                new BigDecimal("0.020")));

        facilityRepository.save(facility);
        LOG.info("Seeded demo facility {} (ACTIVE). Disable with DWARA_SEED_DEMO_FACILITY=false.",
                facilityCode);
    }
}

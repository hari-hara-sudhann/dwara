package me.hari.dwara.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import me.hari.dwara.entities.enums.ParkingFacilityStatus;
import me.hari.dwara.entities.records.PricingPolicy;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.UUID;

@Entity
@Getter
@Setter
public class ParkingFacility {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID facilityId;

    private String name;
    private String address;

    @Column(nullable = false, unique = true)
    private String facilityCode;

    @Enumerated(EnumType.STRING)
    private ParkingFacilityStatus status;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    private PricingPolicy pricingPolicy;
}

package me.hari.dwara.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import me.hari.dwara.entities.enums.Role;

import java.time.Instant;
import java.util.UUID;

@Entity
@Getter
@Setter
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID userId;

    @Column(unique = true)
    private String email;

    private String name;

    @Enumerated(EnumType.STRING)
    private Role role;

    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = Instant.now();
    }
}

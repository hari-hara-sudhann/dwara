package me.hari.dwara.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import me.hari.dwara.entities.enums.UserRole;

import java.time.Instant;
import java.util.UUID;

// "user" is a reserved word in PostgreSQL, so the default Hibernate table name
// produces `create table user (...)` and `... references user`, both of which
// are syntax errors. Everything else about the mapping is unchanged.
@Entity
@Table(name = "users")
@Getter
@Setter
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID userId;

    @Column(unique = true)
    private String email;

    private String name;

    @Column(name = "password")
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    private UserRole userRole;

    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = Instant.now();
    }
}

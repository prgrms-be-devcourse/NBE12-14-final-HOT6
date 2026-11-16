package com.back.nbe1214finalhot6.domain.auth.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String username;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false, unique = true)
    private String phone;

    @Column(nullable = false)
    private String pin;

    @Column(nullable = false)
    private int pinFailCount;

    @Column(nullable = false)
    private boolean pinLocked;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserRole role;

    private User(String username, String email, String phone, String password, String pin,
        UserRole role) {
        this.username = username;
        this.email = email;
        this.phone = phone;
        this.password = password;
        this.pin = pin;
        this.role = role;
    }

    public static User create(String username, String email, String phone, String encodedPassword,
        String encodedPin, UserRole role) {
        return new User(username, email, phone, encodedPassword, encodedPin, role);
    }
}

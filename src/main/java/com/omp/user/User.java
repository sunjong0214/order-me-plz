package com.omp.user;

import static jakarta.persistence.GenerationType.IDENTITY;
import static lombok.AccessLevel.PROTECTED;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "USERS")
@NoArgsConstructor(access = PROTECTED)
@Getter
public class User {
    @Id
    @GeneratedValue(strategy = IDENTITY)
    @Column(name = "user_id")
    private Long id;
    @NotBlank
    private String email;
    @NotBlank
    private String name;
    @Enumerated(EnumType.STRING)
    private UserStatus status;

    public User(String email, String name) {
        this.email = email;
        this.name = name;
    }

    public boolean isBan() {
        return status == UserStatus.BAN;
    }
}

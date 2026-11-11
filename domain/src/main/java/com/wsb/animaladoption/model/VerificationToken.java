package com.wsb.animaladoption.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Data
@NoArgsConstructor
public class VerificationToken {
    @Id
    @GeneratedValue(strategy =  GenerationType.IDENTITY)
    private Long id;

    private String token;

    @OneToOne(targetEntity = User.class, fetch = FetchType.EAGER)
    @JoinColumn(nullable = false, name = "user_id")
    private User user;

    private LocalDateTime expiryDate;

    public VerificationToken(String token, User user) {
        this.user = user;
        renew(token);
    }

    /**
     * Replaces the token value and restarts its 24-hour validity.
     * Used when the user already has a token (e.g. changes their email again) - the relation is one-to-one,
     * so the existing row is reused instead of creating a second one.
     */
    public void renew(String newToken) {
        this.token = newToken;
        this.expiryDate = LocalDateTime.now().plusHours(24);
    }

}

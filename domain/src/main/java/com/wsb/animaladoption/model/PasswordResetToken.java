package com.wsb.animaladoption.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * One-time link for setting a new password ("Forgot password?").
 * <p>
 * Only a SHA-256 hash of the token is stored, never the token itself - the token works like a password,
 * so if someone read the database (e.g. a leaked backup), they still could not use the links.
 * The token is short-lived (see {@link #VALIDITY}) and deleted after use.
 * <p>
 * A separate entity from {@link VerificationToken} on purpose: that one confirms email addresses and lives 24 hours,
 * while a password reset link gives access to the account, so it must expire much faster.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
public class PasswordResetToken {

    /** How long the link from the email works. */
    public static final Duration VALIDITY = Duration.ofHours(1);

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** SHA-256 of the token from the link (hex), so lookups are still possible without storing the token. */
    @Column(nullable = false, unique = true, length = 64)
    private String tokenHash;

    /** A user has at most one active link - asking again replaces the previous one. */
    @OneToOne(fetch = FetchType.EAGER)
    @JoinColumn(nullable = false, unique = true, name = "user_id")
    private User user;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime expiryDate;

    public PasswordResetToken(String tokenHash, User user) {
        this.user = user;
        renew(tokenHash);
    }

    /** Replaces the token and restarts its validity (reuses the row, because the relation is one-to-one). */
    public void renew(String newTokenHash) {
        this.tokenHash = newTokenHash;
        this.createdAt = LocalDateTime.now();
        this.expiryDate = createdAt.plus(VALIDITY);
    }

    public boolean isExpired() {
        return !expiryDate.isAfter(LocalDateTime.now());
    }
}

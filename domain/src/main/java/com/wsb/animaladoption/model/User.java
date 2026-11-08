package com.wsb.animaladoption.model;

import com.wsb.animaladoption.enums.RoleEnum;
import com.wsb.animaladoption.security.StringCryptoConverter;
import com.wsb.animaladoption.enums.UserTypeEnum;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String email;

    /**
     * New email address waiting for confirmation. When the user changes their email in the profile,
     * the current address stays active until they click the link sent to the new one -
     * this way a typo in the new address cannot lock them out of the account.
     */
    private String pendingEmail;

    @Column(nullable = false)
    private String passwordHash;

    @Column(nullable = false, length = 100)
    private String displayName;

    @Convert(converter = StringCryptoConverter.class)
    @Column(nullable = false, length = 100) // length increased because the encrypted text will be longer
    private String mobile;

    @Enumerated(EnumType.STRING)
    private RoleEnum role;

    @Enumerated(EnumType.STRING)
    private UserTypeEnum userType;

    private boolean isEmailVerified = false;

    @Column(columnDefinition = "TEXT")
    private String description;

    private String avatarUrl;

    @CreationTimestamp
    private LocalDateTime createdAt;

    /**
     * Account blocked by a moderator until this moment (null - not blocked). A ban always has an end,
     * so there is no separate "unblock" job - after this time the account simply works again.
     */
    private LocalDateTime bannedUntil;

    /** Reason of the ban, shown to the user when they try to log in. */
    @Column(length = 500)
    private String banReason;

    /** Whether the account is blocked right now. */
    public boolean isBanned() {
        return bannedUntil != null && bannedUntil.isAfter(LocalDateTime.now());
    }

    /** Moderators and administrators - accounts that cannot be blocked from the moderation panel. */
    public boolean isStaff() {
        return role == RoleEnum.MODERATOR || role == RoleEnum.ADMIN;
    }
}

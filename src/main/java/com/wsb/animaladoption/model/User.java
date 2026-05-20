package com.wsb.animaladoption.model;

import com.wsb.animaladoption.enums.RoleEnum;
import com.wsb.animaladoption.security.StringCryptoConverter;
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

    @Column(nullable = false)
    private String passwordHash;

    @Column(nullable = false, length = 100)
    private String displayName;

    @Convert(converter = StringCryptoConverter.class)
    @Column(nullable = false, length = 100) // zwiększyłem length, bo za szyfrowany tekst będzie dłuższy
    private String mobile;

    @Enumerated(EnumType.STRING)
    private RoleEnum role;

    private boolean isEmailVerified = false;

    @Column(columnDefinition = "TEXT")
    private String description;

    private String avatarUrl;

    @CreationTimestamp
    private LocalDateTime createdAt;


}

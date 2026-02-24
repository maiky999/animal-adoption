package com.wsb.animaladoption.model;

import com.wsb.animaladoption.enums.RoleEnum;
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

    @Column(nullable = false, length = 9)
    private String mobile;

    @Enumerated(EnumType.STRING)
    private RoleEnum role;

    private boolean isEmailVerified = false;

    @CreationTimestamp
    private LocalDateTime createdAt;


}

package com.wsb.animaladoption.repository;

import com.wsb.animaladoption.model.User;
import com.wsb.animaladoption.model.VerificationToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface VerificationTokenRepository extends JpaRepository<VerificationToken, Long> {
    Optional<VerificationToken> findByToken(String token);

    /** A user has at most one active token (one-to-one relation) - e.g. a new email change replaces the previous one. */
    Optional<VerificationToken> findByUser(User user);
}

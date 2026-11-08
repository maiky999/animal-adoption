package com.wsb.animaladoption.repository;

import com.wsb.animaladoption.model.User;
import com.wsb.animaladoption.model.VerificationToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface VerificationTokenRepository extends JpaRepository<VerificationToken, Long> {
    Optional<VerificationToken> findByToken(String token);

    /** A user has at most one active token (one-to-one relation) - e.g. a new email change replaces the previous one. */
    Optional<VerificationToken> findByUser(User user);

    /** Expired activation links of accounts that were never confirmed (e.g. created by bots or with a typo in the email). */
    @Query("select t from VerificationToken t where t.expiryDate < :now and t.user.isEmailVerified = false")
    List<VerificationToken> findExpiredOfUnverifiedUsers(@Param("now") LocalDateTime now);
}

package com.wsb.animaladoption.repository;

import com.wsb.animaladoption.model.PasswordResetToken;
import com.wsb.animaladoption.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {
    Optional<PasswordResetToken> findByTokenHash(String tokenHash);

    Optional<PasswordResetToken> findByUser(User user);

    /** Removes links nobody used - they cannot be used anyway, so there is no reason to keep them. */
    @Modifying
    @Query("delete from PasswordResetToken t where t.expiryDate < :now")
    int deleteExpired(@Param("now") LocalDateTime now);
}

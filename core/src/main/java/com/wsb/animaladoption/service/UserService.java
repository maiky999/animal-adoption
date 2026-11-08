package com.wsb.animaladoption.service;

import com.wsb.animaladoption.dto.UserPasswordUpdateDto;
import com.wsb.animaladoption.dto.UserProfileUpdateDto;
import com.wsb.animaladoption.dto.UserRegistrationDto;
import com.wsb.animaladoption.dto.VerificationResult;
import com.wsb.animaladoption.model.User;

/**
 * Business logic for user accounts: registration, email verification and profile management.
 */
public interface UserService {

    /** Registers a new user and sends an email with a verification link. */
    User registerNewUser(UserRegistrationDto dto);

    /**
     * Handles a verification link: activates a new account or, if the user requested an email change,
     * replaces their email with the confirmed new address.
     */
    VerificationResult verifyEmail(String token);

    /**
     * Updates the user's profile data (including the avatar).
     * A changed email is not applied immediately - it is stored as pending and a confirmation link
     * is sent to the new address (see {@link #verifyEmail(String)}).
     *
     * @return {@code true} if an email change confirmation was sent
     * @throws IllegalArgumentException if the new email is already used by another account
     */
    boolean updateUserProfile(String currentEmail, UserProfileUpdateDto dto);

    /** Checks whether an account with the given email exists. */
    boolean existsByEmail(String email);

    /**
     * Changes the user's password after verifying the current password.
     *
     * @throws IllegalArgumentException if the current password is incorrect
     */
    void changeUserPassword(String email, UserPasswordUpdateDto dto);

    /**
     * Returns the user with the given email address.
     *
     * @throws IllegalArgumentException if the user does not exist
     */
    User findByEmail(String email);

    /**
     * Returns the user with the given id.
     *
     * @throws IllegalArgumentException if the user does not exist
     */
    User findById(Long id);

    /**
     * Deletes accounts whose email was never confirmed and whose activation link has already expired.
     * Such an account cannot log in, so it only takes up the email address and space in the database.
     *
     * @return number of deleted accounts
     */
    int deleteExpiredUnverifiedAccounts();
}

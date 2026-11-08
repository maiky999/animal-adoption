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
     * Sends a link for setting a new password ("Forgot password?").
     * <p>
     * The method behaves the same whether the account exists or not (no exception, no return value),
     * so the form cannot be used to check which email addresses are registered.
     * The link is sent only to confirmed accounts, and not more often than every few minutes.
     */
    void requestPasswordReset(String email);

    /** Checks whether the link from the email is still valid (to show the form or an error right away). */
    boolean isPasswordResetTokenValid(String token);

    /**
     * Sets a new password using the link from the email. The link works only once.
     *
     * @return {@code false} if the link is invalid, already used or expired
     */
    boolean resetPassword(String token, String newPassword);

    /** Deletes unused password reset links that have already expired. */
    int deleteExpiredPasswordResetTokens();

    /**
     * Deletes accounts whose email was never confirmed and whose activation link has already expired.
     * Such an account cannot log in, so it only takes up the email address and space in the database.
     *
     * @return number of deleted accounts
     */
    int deleteExpiredUnverifiedAccounts();
}

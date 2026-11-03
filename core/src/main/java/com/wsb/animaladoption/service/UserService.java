package com.wsb.animaladoption.service;

import com.wsb.animaladoption.dto.UserPasswordUpdateDto;
import com.wsb.animaladoption.dto.UserProfileUpdateDto;
import com.wsb.animaladoption.dto.UserRegistrationDto;
import com.wsb.animaladoption.model.User;

/**
 * Business logic for user accounts: registration, email verification and profile management.
 */
public interface UserService {

    /** Registers a new user and sends an email with a verification link. */
    User registerNewUser(UserRegistrationDto dto);

    /**
     * Activates the account based on the verification token.
     *
     * @return {@code true} if the token exists and has not expired
     */
    boolean verifyEmail(String token);

    /**
     * Updates the user's profile data (including the avatar).
     *
     * @throws IllegalArgumentException if the new email is already used by another account
     */
    void updateUserProfile(String currentEmail, UserProfileUpdateDto dto);

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
}

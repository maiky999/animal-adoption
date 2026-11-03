package com.wsb.animaladoption.service;

import com.wsb.animaladoption.dto.UserPasswordUpdateDto;
import com.wsb.animaladoption.dto.UserProfileUpdateDto;
import com.wsb.animaladoption.dto.UserRegistrationDto;
import com.wsb.animaladoption.model.User;

/**
 * Logika biznesowa kont użytkowników: rejestracja, weryfikacja e-mail i zarządzanie profilem.
 */
public interface UserService {

    /** Rejestruje nowego użytkownika i wysyła e-mail z linkiem weryfikacyjnym. */
    User registerNewUser(UserRegistrationDto dto);

    /**
     * Aktywuje konto na podstawie tokenu weryfikacyjnego.
     *
     * @return {@code true}, gdy token istnieje i nie wygasł
     */
    boolean verifyEmail(String token);

    /**
     * Aktualizuje dane profilu użytkownika (w tym avatar).
     *
     * @throws IllegalArgumentException gdy nowy e-mail jest zajęty przez inne konto
     */
    void updateUserProfile(String currentEmail, UserProfileUpdateDto dto);

    /**
     * Zmienia hasło użytkownika po weryfikacji obecnego hasła.
     *
     * @throws IllegalArgumentException gdy obecne hasło jest nieprawidłowe
     */
    void changeUserPassword(String email, UserPasswordUpdateDto dto);

    /**
     * Zwraca użytkownika o podanym adresie e-mail.
     *
     * @throws IllegalArgumentException gdy użytkownik nie istnieje
     */
    User findByEmail(String email);

    /**
     * Zwraca użytkownika o podanym id.
     *
     * @throws IllegalArgumentException gdy użytkownik nie istnieje
     */
    User findById(Long id);
}

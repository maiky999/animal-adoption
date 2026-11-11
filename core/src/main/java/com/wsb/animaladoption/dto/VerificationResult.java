package com.wsb.animaladoption.dto;

import com.wsb.animaladoption.enums.VerificationResultEnum;

/**
 * Outcome of clicking a verification link.
 *
 * @param status        what happened
 * @param previousEmail the account's address before the change (only for {@link VerificationResultEnum#EMAIL_CHANGED}) -
 *                      lets the controller recognize that the logged-in user is the owner of the changed account
 * @param currentEmail  the account's address after the operation, or {@code null} for an invalid token
 */
public record VerificationResult(VerificationResultEnum status, String previousEmail, String currentEmail) {

    public static VerificationResult invalid() {
        return new VerificationResult(VerificationResultEnum.INVALID, null, null);
    }
}

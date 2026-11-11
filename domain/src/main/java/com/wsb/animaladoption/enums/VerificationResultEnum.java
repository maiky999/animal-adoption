package com.wsb.animaladoption.enums;

/** Result of clicking a verification link sent by email. */
public enum VerificationResultEnum {
    /** A new account was activated (link from the registration email). */
    ACCOUNT_VERIFIED,
    /** The account's email was changed to the confirmed new address (link from the email change). */
    EMAIL_CHANGED,
    /** The new address was taken by another account in the meantime - the change was cancelled. */
    EMAIL_TAKEN,
    /** The token does not exist or has expired. */
    INVALID
}

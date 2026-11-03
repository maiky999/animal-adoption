package com.wsb.animaladoption.enums;

public enum UserTypeEnum {
    PRIVATE,
    ORGANIZATION;

    /** Klucz tłumaczenia nazwy typu konta (np. userType.PRIVATE) - paczka i18n/common. */
    public String getMessageKey() {
        return "userType." + name();
    }
}

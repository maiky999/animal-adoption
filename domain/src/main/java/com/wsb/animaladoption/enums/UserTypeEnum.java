package com.wsb.animaladoption.enums;

public enum UserTypeEnum {
    PRIVATE,
    ORGANIZATION;

    /** Translation key of the account type name (e.g. userType.PRIVATE) - i18n/common bundle. */
    public String getMessageKey() {
        return "userType." + name();
    }
}

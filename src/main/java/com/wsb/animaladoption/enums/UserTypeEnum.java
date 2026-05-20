package com.wsb.animaladoption.enums;

public enum UserTypeEnum {
    PRIVATE("Osoba prywatna"),
    ORGANIZATION("Organizacja / Fundacja");

    private final String label;

    UserTypeEnum(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}

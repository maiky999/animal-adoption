package com.wsb.animaladoption.enums;

public enum AdStatusEnum {
    PENDING,
    ACTIVE,
    ADOPTED,
    ARCHIVED,
    REJECTED,
    /**
     * Removed by a moderator (e.g. a published listing that breaks the rules). The row stays in the database,
     * because conversations about the listing refer to it - it is only hidden and cannot be edited any more.
     */
    REMOVED
}

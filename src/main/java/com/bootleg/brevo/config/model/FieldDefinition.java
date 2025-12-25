package com.bootleg.brevo.config.model;

public record FieldDefinition(
    String fieldCode,
    boolean required,
    int sortOrder
) {}

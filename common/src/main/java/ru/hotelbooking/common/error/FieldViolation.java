package ru.hotelbooking.common.error;

public record FieldViolation(String field, String message) {}

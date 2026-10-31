package ru.hotelbooking.common.security;

public final class Authorities {

    public static final String USER = "hasRole('USER')";
    public static final String ADMIN = "hasRole('ADMIN')";
    public static final String INTERNAL = "hasRole('INTERNAL')";
    public static final String USER_OR_INTERNAL = "hasAnyRole('USER', 'INTERNAL')";

    private Authorities() {}
}

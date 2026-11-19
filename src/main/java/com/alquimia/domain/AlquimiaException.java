package com.alquimia.domain;

public final class AlquimiaException extends RuntimeException {
    public enum Code { INVALID_DATA, UNKNOWN_DEVICE, NOT_FOUND, ALREADY_REGISTERED,
        INSUFFICIENT_DATA, INCONSISTENT_DISTANCES, INCOMPLETE_MESSAGE, CONFLICTING_MESSAGE }

    private final Code code;

    public AlquimiaException(Code code, String message) {
        super(message);
        this.code = code;
    }

    public Code code() { return code; }
}

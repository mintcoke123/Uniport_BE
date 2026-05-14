package com.uniport.entity;

import com.uniport.exception.ApiException;
import org.springframework.http.HttpStatus;

import java.util.Locale;

public enum PushPermissionStatus {
    GRANTED("granted"),
    DENIED("denied"),
    NOT_DETERMINED("not_determined"),
    UNSUPPORTED("unsupported");

    private final String wireValue;

    PushPermissionStatus(String wireValue) {
        this.wireValue = wireValue;
    }

    public String getWireValue() {
        return wireValue;
    }

    public boolean allowsDelivery() {
        return this == GRANTED;
    }

    public static PushPermissionStatus fromWireValue(String value) {
        if (value == null || value.isBlank()) {
            throw new ApiException("permissionStatus is required", HttpStatus.BAD_REQUEST);
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        for (PushPermissionStatus status : values()) {
            if (status.wireValue.equals(normalized)) {
                return status;
            }
        }
        throw new ApiException(
                "permissionStatus must be granted, denied, not_determined, or unsupported",
                HttpStatus.BAD_REQUEST
        );
    }
}

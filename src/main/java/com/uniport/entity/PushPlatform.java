package com.uniport.entity;

import com.uniport.exception.ApiException;
import org.springframework.http.HttpStatus;

import java.util.Locale;

public enum PushPlatform {
    ANDROID("android"),
    IOS("ios");

    private final String wireValue;

    PushPlatform(String wireValue) {
        this.wireValue = wireValue;
    }

    public String getWireValue() {
        return wireValue;
    }

    public static PushPlatform fromWireValue(String value) {
        if (value == null || value.isBlank()) {
            throw new ApiException("platform is required", HttpStatus.BAD_REQUEST);
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        for (PushPlatform platform : values()) {
            if (platform.wireValue.equals(normalized)) {
                return platform;
            }
        }
        throw new ApiException("platform must be android or ios", HttpStatus.BAD_REQUEST);
    }
}

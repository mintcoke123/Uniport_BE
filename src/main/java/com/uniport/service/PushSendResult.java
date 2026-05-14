package com.uniport.service;

public class PushSendResult {

    private final boolean success;
    private final boolean invalidToken;

    private PushSendResult(boolean success, boolean invalidToken) {
        this.success = success;
        this.invalidToken = invalidToken;
    }

    public static PushSendResult success() {
        return new PushSendResult(true, false);
    }

    public static PushSendResult invalidToken() {
        return new PushSendResult(false, true);
    }

    public static PushSendResult failure() {
        return new PushSendResult(false, false);
    }

    public boolean isSuccess() {
        return success;
    }

    public boolean isInvalidToken() {
        return invalidToken;
    }
}

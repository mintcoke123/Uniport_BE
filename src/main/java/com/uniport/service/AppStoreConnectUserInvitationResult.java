package com.uniport.service;

public record AppStoreConnectUserInvitationResult(
        boolean sent,
        boolean skipped,
        boolean duplicate,
        String invitationId,
        String message
) {
    public static AppStoreConnectUserInvitationResult sent(String invitationId) {
        return new AppStoreConnectUserInvitationResult(true, false, false, invitationId, "Apple 초대 메일을 보냈습니다.");
    }

    public static AppStoreConnectUserInvitationResult duplicate(String message) {
        return new AppStoreConnectUserInvitationResult(true, false, true, null, message);
    }

    public static AppStoreConnectUserInvitationResult skipped(String message) {
        return new AppStoreConnectUserInvitationResult(false, true, false, null, message);
    }

    public static AppStoreConnectUserInvitationResult failed(String message) {
        return new AppStoreConnectUserInvitationResult(false, false, false, null, message);
    }
}

package com.uniport.service;

public record AppStoreConnectUserInvitationResult(
        boolean sent,
        boolean skipped,
        String invitationId,
        String message
) {
    public static AppStoreConnectUserInvitationResult sent(String invitationId) {
        return new AppStoreConnectUserInvitationResult(true, false, invitationId, "Apple 초대 메일을 보냈습니다.");
    }

    public static AppStoreConnectUserInvitationResult skipped(String message) {
        return new AppStoreConnectUserInvitationResult(false, true, null, message);
    }

    public static AppStoreConnectUserInvitationResult failed(String message) {
        return new AppStoreConnectUserInvitationResult(false, false, null, message);
    }
}

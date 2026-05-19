package com.uniport.service;

public record AppStoreConnectBetaGroupSyncResult(
        boolean added,
        boolean pending,
        boolean skipped,
        String betaTesterId,
        String groupId,
        String message
) {
    public static AppStoreConnectBetaGroupSyncResult added(String betaTesterId, String groupId) {
        return new AppStoreConnectBetaGroupSyncResult(
                true,
                false,
                false,
                betaTesterId,
                groupId,
                "TestFlight internal group relationship is ready."
        );
    }

    public static AppStoreConnectBetaGroupSyncResult pending(String message) {
        return new AppStoreConnectBetaGroupSyncResult(false, true, false, null, null, message);
    }

    public static AppStoreConnectBetaGroupSyncResult skipped(String message) {
        return new AppStoreConnectBetaGroupSyncResult(false, false, true, null, null, message);
    }

    public static AppStoreConnectBetaGroupSyncResult failed(String message) {
        return new AppStoreConnectBetaGroupSyncResult(false, false, false, null, null, message);
    }
}

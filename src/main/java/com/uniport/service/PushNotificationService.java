package com.uniport.service;

import com.uniport.dto.PushTestRequestDTO;
import com.uniport.dto.PushTestResponseDTO;
import com.uniport.entity.MatchingRoom;
import com.uniport.entity.User;
import com.uniport.entity.UserPushToken;
import com.uniport.entity.Vote;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
public class PushNotificationService {

    private final PushTokenService pushTokenService;
    private final PushMessageSender pushMessageSender;
    private final String publicBaseUrl;

    public PushNotificationService(PushTokenService pushTokenService,
                                   PushMessageSender pushMessageSender,
                                   @Value("${app.public-base-url:https://uniportbe-production.up.railway.app}") String publicBaseUrl) {
        this.pushTokenService = pushTokenService;
        this.pushMessageSender = pushMessageSender;
        this.publicBaseUrl = trimTrailingSlash(publicBaseUrl);
    }

    public void sendVoteCreated(Long roomId, Vote vote, List<Long> recipientUserIds) {
        if (roomId == null || vote == null || vote.getId() == null || recipientUserIds == null || recipientUserIds.isEmpty()) {
            return;
        }
        String stockName = valueOrDefault(vote.getStockName(), "종목");
        String type = valueOrDefault(vote.getType(), "거래");
        sendToUsers(
                recipientUserIds,
                "새 투표가 시작됐어요",
                stockName + " " + type + " 투표에 참여해 주세요.",
                Map.of(
                        "type", "vote_created",
                        "deeplink", publicBaseUrl + "/matching-room?roomId=" + roomId,
                        "entityId", String.valueOf(vote.getId()),
                        "roomId", String.valueOf(roomId),
                        "voteId", String.valueOf(vote.getId())
                )
        );
    }

    public void sendMatchingRoomInvite(MatchingRoom room, List<User> invitees, User host) {
        if (room == null || room.getId() == null || invitees == null || invitees.isEmpty()) {
            return;
        }
        List<Long> recipientUserIds = invitees.stream()
                .filter(Objects::nonNull)
                .map(User::getId)
                .filter(Objects::nonNull)
                .toList();
        String inviteCode = valueOrDefault(room.getInviteCode(), "");
        String hostName = host != null ? valueOrDefault(host.getNickname(), "친구") : "친구";
        sendToUsers(
                recipientUserIds,
                "매칭방 초대가 도착했어요",
                hostName + "님이 매칭방에 초대했어요.",
                Map.of(
                        "type", "matching_room_invite",
                        "deeplink", publicBaseUrl + "/matching-room?inviteCode=" + inviteCode + "&roomId=" + room.getId(),
                        "entityId", String.valueOf(room.getId()),
                        "roomId", String.valueOf(room.getId()),
                        "inviteCode", inviteCode
                )
        );
    }

    public PushTestResponseDTO sendTestPush(User user, PushTestRequestDTO request) {
        if (user == null || user.getId() == null) {
            return deliverySummary(0, 0, 0);
        }
        String title = valueOrDefault(request != null ? request.getTitle() : null, "Uniport 테스트 알림");
        String body = valueOrDefault(request != null ? request.getBody() : null, "백엔드에서 보낸 테스트 푸시입니다.");
        String deeplink = valueOrDefault(request != null ? request.getDeeplink() : null, publicBaseUrl);
        DeliverySummary summary = sendToUsers(
                List.of(user.getId()),
                title,
                body,
                Map.of(
                        "type", "test_push",
                        "deeplink", deeplink,
                        "entityId", "push-test"
                )
        );
        return deliverySummary(summary.attempted, summary.success, summary.failed);
    }

    private DeliverySummary sendToUsers(List<Long> userIds, String title, String body, Map<String, String> data) {
        int attempted = 0;
        int success = 0;
        int failed = 0;
        Set<Long> distinctUserIds = new LinkedHashSet<>(userIds);
        for (Long userId : distinctUserIds) {
            if (userId == null) {
                continue;
            }
            List<UserPushToken> tokens = pushTokenService.getDeliverableTokens(userId);
            for (UserPushToken token : tokens) {
                if (token == null || token.getToken() == null || token.getToken().isBlank()) {
                    continue;
                }
                PushSendResult result = pushMessageSender.send(new PushMessage(token.getToken(), title, body, data));
                attempted++;
                if (result.isSuccess()) {
                    success++;
                } else {
                    failed++;
                }
                if (result.isInvalidToken()) {
                    pushTokenService.markTokenInvalid(token.getToken());
                }
            }
        }
        return new DeliverySummary(attempted, success, failed);
    }

    private static String trimTrailingSlash(String value) {
        if (value == null || value.isBlank()) {
            return "https://uniportbe-production.up.railway.app";
        }
        String trimmed = value.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }

    private static String valueOrDefault(String value, String fallback) {
        return value != null && !value.isBlank() ? value.trim() : fallback;
    }

    private static PushTestResponseDTO deliverySummary(int attempted, int success, int failed) {
        return PushTestResponseDTO.builder()
                .attempted(attempted)
                .success(success)
                .failed(failed)
                .build();
    }

    private static class DeliverySummary {
        private final int attempted;
        private final int success;
        private final int failed;

        private DeliverySummary(int attempted, int success, int failed) {
            this.attempted = attempted;
            this.success = success;
            this.failed = failed;
        }
    }
}

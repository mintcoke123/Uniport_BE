package com.uniport.service;

import com.uniport.entity.MatchingRoom;
import com.uniport.entity.User;
import com.uniport.entity.UserPushToken;
import com.uniport.entity.Vote;
import com.uniport.dto.PushTestRequestDTO;
import com.uniport.dto.PushTestResponseDTO;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PushNotificationServiceTest {

    @Test
    void sendVoteCreatedSendsDataPayloadToDeliverableTokensAndDisablesInvalidToken() {
        FakePushTokenService pushTokenService = new FakePushTokenService();
        pushTokenService.tokensByUserId.put(8L, List.of(pushToken("token-active")));
        pushTokenService.tokensByUserId.put(9L, List.of(pushToken("token-invalid")));
        FakePushMessageSender sender = new FakePushMessageSender("token-invalid");
        PushNotificationService service = new PushNotificationService(
                pushTokenService,
                sender,
                "https://uniportbe-production.up.railway.app"
        );
        Vote vote = Vote.builder()
                .id(456L)
                .roomId(123L)
                .type("매수")
                .stockName("삼성전자")
                .stockCode("005930")
                .build();

        service.sendVoteCreated(123L, vote, List.of(8L, 9L));

        assertEquals(2, sender.messages.size());
        PushMessage message = sender.messages.get(0);
        assertEquals("token-active", message.getToken());
        assertEquals("새 투표가 시작됐어요", message.getTitle());
        assertTrue(message.getBody().contains("삼성전자"));
        assertEquals("vote_created", message.getData().get("type"));
        assertEquals("456", message.getData().get("entityId"));
        assertEquals("123", message.getData().get("roomId"));
        assertEquals("456", message.getData().get("voteId"));
        assertEquals(
                "https://uniportbe-production.up.railway.app/matching-room?roomId=123",
                message.getData().get("deeplink")
        );
        assertEquals(List.of("token-invalid"), pushTokenService.invalidTokens);
    }

    @Test
    void sendMatchingRoomInviteSendsInvitePayloadToInvitees() {
        FakePushTokenService pushTokenService = new FakePushTokenService();
        pushTokenService.tokensByUserId.put(8L, List.of(pushToken("friend-token")));
        FakePushMessageSender sender = new FakePushMessageSender();
        PushNotificationService service = new PushNotificationService(
                pushTokenService,
                sender,
                "https://uniportbe-production.up.railway.app/"
        );
        MatchingRoom room = MatchingRoom.create("Friend Match Room", 3);
        room.setId(123L);
        room.setInviteCode("abc123");
        User host = User.builder().id(7L).nickname("방장").build();
        User friend = User.builder().id(8L).nickname("친구").build();

        service.sendMatchingRoomInvite(room, List.of(friend), host);

        assertEquals(1, sender.messages.size());
        PushMessage message = sender.messages.get(0);
        assertEquals("friend-token", message.getToken());
        assertEquals("매칭방 초대가 도착했어요", message.getTitle());
        assertTrue(message.getBody().contains("방장"));
        assertEquals("matching_room_invite", message.getData().get("type"));
        assertEquals("123", message.getData().get("entityId"));
        assertEquals("123", message.getData().get("roomId"));
        assertEquals("abc123", message.getData().get("inviteCode"));
        assertEquals(
                "https://uniportbe-production.up.railway.app/matching-room?inviteCode=abc123&roomId=123",
                message.getData().get("deeplink")
        );
    }

    @Test
    void sendChatRoomCreatedSendsRoomPayloadToAllMembers() {
        FakePushTokenService pushTokenService = new FakePushTokenService();
        pushTokenService.tokensByUserId.put(7L, List.of(pushToken("first-token")));
        pushTokenService.tokensByUserId.put(8L, List.of(pushToken("second-token")));
        FakePushMessageSender sender = new FakePushMessageSender();
        PushNotificationService service = new PushNotificationService(
                pushTokenService,
                sender,
                "https://uniportbe-production.up.railway.app"
        );
        MatchingRoom room = MatchingRoom.create("랜덤 매칭방", 3);
        room.setId(260L);

        service.sendChatRoomCreated(room, List.of(7L, 8L));

        assertEquals(2, sender.messages.size());
        PushMessage message = sender.messages.get(0);
        assertEquals("first-token", message.getToken());
        assertEquals("채팅방이 생성됐어요", message.getTitle());
        assertTrue(message.getBody().contains("랜덤 매칭방"));
        assertEquals("chat_room_created", message.getData().get("type"));
        assertEquals("260", message.getData().get("entityId"));
        assertEquals("260", message.getData().get("roomId"));
        assertEquals(
                "https://uniportbe-production.up.railway.app/matching-room?roomId=260",
                message.getData().get("deeplink")
        );
    }

    @Test
    void sendChatMentionAllSendsMentionPayloadToRoomMembersExceptCaller() {
        FakePushTokenService pushTokenService = new FakePushTokenService();
        pushTokenService.tokensByUserId.put(8L, List.of(pushToken("member-token")));
        FakePushMessageSender sender = new FakePushMessageSender();
        PushNotificationService service = new PushNotificationService(
                pushTokenService,
                sender,
                "https://uniportbe-production.up.railway.app"
        );
        User caller = User.builder().id(7L).nickname("호출자").build();

        service.sendChatMentionAll(260L, caller, List.of(7L, 8L));

        assertEquals(1, sender.messages.size());
        PushMessage message = sender.messages.get(0);
        assertEquals("member-token", message.getToken());
        assertEquals("채팅방에서 호출했어요", message.getTitle());
        assertTrue(message.getBody().contains("호출자"));
        assertEquals("chat_mention_all", message.getData().get("type"));
        assertEquals("260", message.getData().get("entityId"));
        assertEquals("260", message.getData().get("roomId"));
        assertEquals("7", message.getData().get("callerId"));
        assertEquals(
                "https://uniportbe-production.up.railway.app/matching-room?roomId=260",
                message.getData().get("deeplink")
        );
    }

    @Test
    void sendChatMessageCreatedSendsMessagePayloadToRoomMembersExceptSender() {
        FakePushTokenService pushTokenService = new FakePushTokenService();
        pushTokenService.tokensByUserId.put(8L, List.of(pushToken("member-token")));
        FakePushMessageSender sender = new FakePushMessageSender();
        PushNotificationService service = new PushNotificationService(
                pushTokenService,
                sender,
                "https://uniportbe-production.up.railway.app"
        );
        User messageSender = User.builder().id(7L).nickname("보낸사람").build();

        service.sendChatMessageCreated(260L, messageSender, "안녕하세요. 오늘 회의합니다.", List.of(7L, 8L));

        assertEquals(1, sender.messages.size());
        PushMessage message = sender.messages.get(0);
        assertEquals("member-token", message.getToken());
        assertEquals("새 채팅이 도착했어요", message.getTitle());
        assertTrue(message.getBody().contains("보낸사람"));
        assertTrue(message.getBody().contains("안녕하세요"));
        assertEquals("chat_message_created", message.getData().get("type"));
        assertEquals("260", message.getData().get("entityId"));
        assertEquals("260", message.getData().get("roomId"));
        assertEquals("7", message.getData().get("senderId"));
        assertEquals(
                "https://uniportbe-production.up.railway.app/matching-room?roomId=260",
                message.getData().get("deeplink")
        );
    }

    @Test
    void sendFriendRequestCreatedSendsRequestPayloadToAddressee() {
        FakePushTokenService pushTokenService = new FakePushTokenService();
        pushTokenService.tokensByUserId.put(8L, List.of(pushToken("friend-request-token")));
        FakePushMessageSender sender = new FakePushMessageSender();
        PushNotificationService service = new PushNotificationService(
                pushTokenService,
                sender,
                "https://uniportbe-production.up.railway.app"
        );
        User requester = User.builder().id(7L).nickname("요청자").build();
        User addressee = User.builder().id(8L).nickname("수신자").build();

        service.sendFriendRequestCreated("REQ_55", requester, addressee);

        assertEquals(1, sender.messages.size());
        PushMessage message = sender.messages.get(0);
        assertEquals("friend-request-token", message.getToken());
        assertEquals("친구 요청이 도착했어요", message.getTitle());
        assertTrue(message.getBody().contains("요청자"));
        assertEquals("friend_request_created", message.getData().get("type"));
        assertEquals("REQ_55", message.getData().get("entityId"));
        assertEquals("7", message.getData().get("requesterId"));
        assertEquals("8", message.getData().get("addresseeId"));
    }

    @Test
    void sendFriendRequestDecisionSendsDecisionPayloadToRequester() {
        FakePushTokenService pushTokenService = new FakePushTokenService();
        pushTokenService.tokensByUserId.put(7L, List.of(pushToken("decision-token")));
        FakePushMessageSender sender = new FakePushMessageSender();
        PushNotificationService service = new PushNotificationService(
                pushTokenService,
                sender,
                "https://uniportbe-production.up.railway.app"
        );
        User requester = User.builder().id(7L).nickname("요청자").build();
        User decider = User.builder().id(8L).nickname("수락자").build();

        service.sendFriendRequestDecision("REQ_55", requester, decider, "ACCEPTED");

        assertEquals(1, sender.messages.size());
        PushMessage message = sender.messages.get(0);
        assertEquals("decision-token", message.getToken());
        assertEquals("친구 요청이 수락됐어요", message.getTitle());
        assertTrue(message.getBody().contains("수락자"));
        assertEquals("friend_request_accepted", message.getData().get("type"));
        assertEquals("REQ_55", message.getData().get("entityId"));
        assertEquals("ACCEPTED", message.getData().get("status"));
    }

    @Test
    void sendFriendInviteAcceptedSendsInvitePayloadToInviter() {
        FakePushTokenService pushTokenService = new FakePushTokenService();
        pushTokenService.tokensByUserId.put(7L, List.of(pushToken("invite-accepted-token")));
        FakePushMessageSender sender = new FakePushMessageSender();
        PushNotificationService service = new PushNotificationService(
                pushTokenService,
                sender,
                "https://uniportbe-production.up.railway.app"
        );
        User inviter = User.builder().id(7L).nickname("초대한사람").build();
        User accepter = User.builder().id(8L).nickname("수락한사람").build();

        service.sendFriendInviteAccepted("INVITE123", inviter, accepter);

        assertEquals(1, sender.messages.size());
        PushMessage message = sender.messages.get(0);
        assertEquals("invite-accepted-token", message.getToken());
        assertEquals("친구 초대가 수락됐어요", message.getTitle());
        assertTrue(message.getBody().contains("수락한사람"));
        assertEquals("friend_invite_accepted", message.getData().get("type"));
        assertEquals("INVITE123", message.getData().get("entityId"));
        assertEquals(
                "https://uniportbe-production.up.railway.app/friend-invite?inviteCode=INVITE123",
                message.getData().get("deeplink")
        );
    }

    @Test
    void sendVoteClosedSendsVoteStatusPayloadToRecipients() {
        FakePushTokenService pushTokenService = new FakePushTokenService();
        pushTokenService.tokensByUserId.put(7L, List.of(pushToken("vote-closed-token")));
        FakePushMessageSender sender = new FakePushMessageSender();
        PushNotificationService service = new PushNotificationService(
                pushTokenService,
                sender,
                "https://uniportbe-production.up.railway.app"
        );
        Vote vote = Vote.builder()
                .id(456L)
                .roomId(260L)
                .type("매수")
                .stockName("삼성전자")
                .status("rejected")
                .build();

        service.sendVoteClosed(260L, vote, List.of(7L));

        assertEquals(1, sender.messages.size());
        PushMessage message = sender.messages.get(0);
        assertEquals("vote-closed-token", message.getToken());
        assertEquals("투표가 마감됐어요", message.getTitle());
        assertTrue(message.getBody().contains("삼성전자"));
        assertEquals("vote_closed", message.getData().get("type"));
        assertEquals("456", message.getData().get("entityId"));
        assertEquals("260", message.getData().get("roomId"));
        assertEquals("456", message.getData().get("voteId"));
        assertEquals("rejected", message.getData().get("status"));
    }

    @Test
    void sendTradeExecutedSendsExecutionPayloadToRoomMembers() {
        FakePushTokenService pushTokenService = new FakePushTokenService();
        pushTokenService.tokensByUserId.put(7L, List.of(pushToken("execution-token")));
        FakePushMessageSender sender = new FakePushMessageSender();
        PushNotificationService service = new PushNotificationService(
                pushTokenService,
                sender,
                "https://uniportbe-production.up.railway.app"
        );
        Vote vote = Vote.builder()
                .id(456L)
                .roomId(260L)
                .type("매수")
                .stockName("삼성전자")
                .build();

        service.sendTradeExecuted(260L, vote, List.of(7L));

        assertEquals(1, sender.messages.size());
        PushMessage message = sender.messages.get(0);
        assertEquals("execution-token", message.getToken());
        assertEquals("체결이 완료됐어요", message.getTitle());
        assertTrue(message.getBody().contains("삼성전자"));
        assertEquals("trade_executed", message.getData().get("type"));
        assertEquals("456", message.getData().get("entityId"));
        assertEquals("260", message.getData().get("roomId"));
        assertEquals("456", message.getData().get("voteId"));
    }

    @Test
    void sendTestPushSendsToCurrentUsersDeliverableTokensAndReturnsSummary() {
        FakePushTokenService pushTokenService = new FakePushTokenService();
        pushTokenService.tokensByUserId.put(7L, List.of(pushToken("token-active"), pushToken("token-invalid")));
        FakePushMessageSender sender = new FakePushMessageSender("token-invalid");
        PushNotificationService service = new PushNotificationService(
                pushTokenService,
                sender,
                "https://uniportbe-production.up.railway.app"
        );
        User currentUser = User.builder().id(7L).nickname("push-user").build();
        PushTestRequestDTO request = PushTestRequestDTO.builder()
                .title("Uniport 테스트 알림")
                .body("백엔드에서 보낸 테스트 푸시입니다.")
                .deeplink("https://uniportbe-production.up.railway.app/matching-room?roomId=10")
                .build();

        PushTestResponseDTO response = service.sendTestPush(currentUser, request);

        assertEquals(2, response.getAttempted());
        assertEquals(1, response.getSuccess());
        assertEquals(1, response.getFailed());
        PushMessage message = sender.messages.get(0);
        assertEquals("token-active", message.getToken());
        assertEquals("Uniport 테스트 알림", message.getTitle());
        assertEquals("백엔드에서 보낸 테스트 푸시입니다.", message.getBody());
        assertEquals("test_push", message.getData().get("type"));
        assertEquals("push-test", message.getData().get("entityId"));
        assertEquals(
                "https://uniportbe-production.up.railway.app/matching-room?roomId=10",
                message.getData().get("deeplink")
        );
        assertEquals(List.of("token-invalid"), pushTokenService.invalidTokens);
    }

    private static UserPushToken pushToken(String value) {
        return UserPushToken.builder()
                .token(value)
                .active(true)
                .build();
    }

    private static class FakePushTokenService extends PushTokenService {
        private final Map<Long, List<UserPushToken>> tokensByUserId = new java.util.HashMap<>();
        private final List<String> invalidTokens = new ArrayList<>();

        FakePushTokenService() {
            super(null, null);
        }

        @Override
        public List<UserPushToken> getDeliverableTokens(Long userId) {
            return tokensByUserId.getOrDefault(userId, List.of());
        }

        @Override
        public void markTokenInvalid(String tokenValue) {
            invalidTokens.add(tokenValue);
        }
    }

    private static class FakePushMessageSender implements PushMessageSender {
        private final List<PushMessage> messages = new ArrayList<>();
        private final String invalidToken;

        FakePushMessageSender() {
            this.invalidToken = "";
        }

        FakePushMessageSender(String invalidToken) {
            this.invalidToken = invalidToken;
        }

        @Override
        public PushSendResult send(PushMessage message) {
            messages.add(message);
            if (message.getToken().equals(invalidToken)) {
                return PushSendResult.invalidToken();
            }
            return PushSendResult.success();
        }
    }
}

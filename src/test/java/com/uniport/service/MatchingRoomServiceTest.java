package com.uniport.service;

import com.uniport.entity.FriendRelation;
import com.uniport.entity.LearningUserStateEntity;
import com.uniport.entity.MatchingRoom;
import com.uniport.entity.MatchingRoomMember;
import com.uniport.entity.User;
import com.uniport.exception.ApiException;
import com.uniport.repository.FriendRelationRepository;
import com.uniport.repository.MatchingRoomMemberRepository;
import com.uniport.repository.MatchingRoomRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:matchingroomservicetest;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.sql.init.mode=never",
        "stock.master.import.enabled=false"
})
@Transactional
class MatchingRoomServiceTest {

    @Autowired
    private MatchingRoomService matchingRoomService;

    @Autowired
    private MatchingRoomMemberRepository matchingRoomMemberRepository;

    @Autowired
    private MatchingRoomRepository matchingRoomRepository;

    @Autowired
    private FriendRelationRepository friendRelationRepository;

    @Autowired
    private EntityManager entityManager;

    @MockitoBean
    private PushNotificationService pushNotificationService;

    @Test
    void quickMatchFriendMode_confirmsInviteesAsMembersImmediately() {
        User host = persistUser("20263001", "matching-host");
        User friend = persistUser("20263002", "matching-friend");
        persistAcceptedFriend(host, friend);
        entityManager.flush();

        Map<String, Object> response = matchingRoomService.quickMatch("FRIEND", "KR", List.of(friend.getId()), host);

        Map<String, Object> detail = map(response.get("detail"));
        assertEquals(2, detail.get("memberCount"));
        assertEquals(2, map(detail.get("progress")).get("current"));
        assertTrue((Boolean) map(detail.get("actions")).get("startEnabled"));

        List<Map<String, Object>> members = list(detail.get("members"));
        assertConfirmedMember(members, host.getId(), "HOST");
        assertConfirmedMember(members, friend.getId(), "MEMBER");
        assertFalse(members.stream().anyMatch(member -> "INVITED".equals(member.get("status"))));
        assertEquals(2, matchingRoomMemberRepository.count());

        ArgumentCaptor<List<User>> inviteesCaptor = ArgumentCaptor.forClass(List.class);
        verify(pushNotificationService).sendMatchingRoomInvite(any(MatchingRoom.class), inviteesCaptor.capture(), any(User.class));
        assertEquals(List.of(friend.getId()), inviteesCaptor.getValue().stream().map(User::getId).toList());
    }

    @Test
    void inviteUsers_confirmsNewInviteesAndIgnoresAlreadyConfirmedMembers() {
        User host = persistUser("20263003", "invite-host");
        User friend = persistUser("20263004", "invite-friend");
        persistAcceptedFriend(host, friend);
        entityManager.flush();

        Map<String, Object> created = matchingRoomService.quickMatch("FRIEND", "KR", List.of(), host);
        String roomId = (String) map(created.get("detail")).get("roomId");

        matchingRoomService.inviteUsers(roomId, List.of(friend.getId()), host);
        Map<String, Object> response = matchingRoomService.inviteUsers(roomId, List.of(friend.getId(), friend.getId()), host);

        Map<String, Object> detail = map(response.get("detail"));
        assertEquals(2, detail.get("memberCount"));
        assertEquals(2, map(detail.get("progress")).get("current"));
        assertTrue((Boolean) map(detail.get("actions")).get("startEnabled"));
        assertEquals(1, matchingRoomMemberRepository.findByUserIdOrderByJoinedAtDesc(friend.getId()).size());
        assertConfirmedMember(list(detail.get("members")), friend.getId(), "MEMBER");
    }

    @Test
    void quickMatchFriendMode_rejectsInviteesWhoAreNotAcceptedFriendsOfHost() {
        User host = persistUser("20263005", "non-friend-host");
        User nonFriend = persistUser("20263006", "non-friend");
        entityManager.flush();

        ApiException exception = assertThrows(
                ApiException.class,
                () -> matchingRoomService.quickMatch("FRIEND", "KR", List.of(nonFriend.getId()), host)
        );

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatus());
    }

    @Test
    void quickMatchFriendMode_rejectsUnknownInviteeUserIds() {
        User host = persistUser("20263011", "unknown-host");
        entityManager.flush();

        ApiException exception = assertThrows(
                ApiException.class,
                () -> matchingRoomService.quickMatch("FRIEND", "KR", List.of(999_999L), host)
        );

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatus());
    }

    @Test
    void quickMatchFriendMode_rejectsInviteesWhoAlreadyParticipateInAnotherRoom() {
        User host = persistUser("20263012", "occupied-host");
        User friend = persistUser("20263013", "occupied-friend");
        persistAcceptedFriend(host, friend);
        entityManager.flush();

        matchingRoomService.quickMatch("RANDOM", "KR", List.of(), friend);

        ApiException exception = assertThrows(
                ApiException.class,
                () -> matchingRoomService.quickMatch("FRIEND", "KR", List.of(friend.getId()), host)
        );

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatus());
    }

    @Test
    void listRoomsJoinedBy_excludesEndedRoomsFromActiveMatchingRooms() {
        User user = persistUser("20263025", "ended-only-user");
        MatchingRoom endedRoom = persistRoomWithMember("종료된 방", "ended", user);
        MatchingRoom waitingRoom = persistRoomWithMember("대기 중인 방", "waiting", user);
        entityManager.flush();

        List<Map<String, Object>> rooms = matchingRoomService.listRoomsJoinedBy(user);

        assertEquals(1, rooms.size());
        assertEquals("room-" + waitingRoom.getId(), rooms.getFirst().get("id"));
        assertFalse(rooms.stream().anyMatch(room -> ("room-" + endedRoom.getId()).equals(room.get("id"))));
    }

    @Test
    void quickMatchRandomMode_allowsUserWhoseOnlyRoomIsEnded() {
        User user = persistUser("20263026", "ended-random-user");
        persistRoomWithMember("종료된 랜덤 방", "ended", user);
        entityManager.flush();

        Map<String, Object> response = matchingRoomService.quickMatch("RANDOM", "KR", List.of(), user);

        assertEquals("RANDOM", response.get("mode"));
        assertEquals("Random match waiting room created.", response.get("message"));
    }

    @Test
    void quickMatchFriendMode_allowsInviteesWhoseOnlyOtherRoomIsEnded() {
        User host = persistUser("20263027", "ended-invite-host");
        User friend = persistUser("20263028", "ended-invite-friend");
        persistAcceptedFriend(host, friend);
        persistRoomWithMember("친구의 종료된 방", "ended", friend);
        entityManager.flush();

        Map<String, Object> response = matchingRoomService.quickMatch("FRIEND", "KR", List.of(friend.getId()), host);

        Map<String, Object> detail = map(response.get("detail"));
        assertEquals(2, detail.get("memberCount"));
        assertConfirmedMember(list(detail.get("members")), friend.getId(), "MEMBER");
    }

    @Test
    void quickMatchFriendMode_rejectsInviteesBeyondRoomCapacity() {
        User host = persistUser("20263007", "capacity-host");
        User friend1 = persistUser("20263008", "capacity-friend-1");
        User friend2 = persistUser("20263009", "capacity-friend-2");
        User friend3 = persistUser("20263010", "capacity-friend-3");
        persistAcceptedFriend(host, friend1);
        persistAcceptedFriend(host, friend2);
        persistAcceptedFriend(host, friend3);
        entityManager.flush();

        ApiException exception = assertThrows(
                ApiException.class,
                () -> matchingRoomService.quickMatch(
                        "FRIEND",
                        "KR",
                        List.of(friend1.getId(), friend2.getId(), friend3.getId()),
                        host
                )
        );

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatus());
    }

    @Test
    void quickMatchRandomMode_keepsWaitingAtTwoMembersUntilManualStart() {
        User firstUser = persistUser("20263017", "random-wait-first");
        User secondUser = persistUser("20263018", "random-wait-second");
        entityManager.flush();

        Map<String, Object> firstResponse = matchingRoomService.quickMatch("RANDOM", "KR", List.of(), firstUser);
        Map<String, Object> firstDetail = map(firstResponse.get("detail"));
        String roomId = (String) firstDetail.get("roomId");
        assertEquals("waiting", findRoom(roomId).getStatus());
        assertFalse((Boolean) map(firstDetail.get("actions")).get("chatEnabled"));
        assertFalse((Boolean) map(firstDetail.get("actions")).get("startEnabled"));

        Map<String, Object> secondResponse = matchingRoomService.quickMatch("RANDOM", "KR", List.of(), secondUser);
        Map<String, Object> secondDetail = map(secondResponse.get("detail"));

        assertEquals(roomId, secondDetail.get("roomId"));
        assertEquals("waiting", findRoom(roomId).getStatus());
        assertEquals(2, secondDetail.get("memberCount"));
        assertFalse((Boolean) map(secondDetail.get("actions")).get("chatEnabled"));
        assertTrue((Boolean) map(secondDetail.get("actions")).get("startEnabled"));
        assertFalse(secondResponse.containsKey("teamId"));

        Map<String, Object> started = matchingRoomService.start(roomId, firstUser);
        Map<String, Object> startedDetail = map(started.get("detail"));
        assertEquals("started", findRoom(roomId).getStatus());
        assertEquals("COMPLETED", startedDetail.get("status"));
        assertTrue((Boolean) map(startedDetail.get("actions")).get("chatEnabled"));
    }

    @Test
    void getRoomDetail_includesRoomCreatedAtForWaitingTimer() {
        User user = persistUser("20263022", "timer-user");
        entityManager.flush();

        Map<String, Object> response = matchingRoomService.quickMatch("RANDOM", "KR", List.of(), user);
        Map<String, Object> detail = map(response.get("detail"));
        String roomId = (String) detail.get("roomId");
        MatchingRoom room = findRoom(roomId);

        assertEquals(room.getCreatedAt().toString(), detail.get("createdAt"));
    }

    @Test
    void getRoomDetail_usesLearningProgressLevelForMembers() {
        User host = persistUser("20263023", "level-host");
        User friend = persistUser("20263024", "level-friend");
        persistAcceptedFriend(host, friend);
        persistLearningState(host, 900);
        persistLearningState(friend, 1_500);
        entityManager.flush();

        Map<String, Object> response = matchingRoomService.quickMatch("FRIEND", "KR", List.of(friend.getId()), host);

        Map<String, Object> detail = map(response.get("detail"));
        List<Map<String, Object>> members = list(detail.get("members"));
        assertEquals(4, memberByUserId(members, host.getId()).get("level"));
        assertEquals(6, memberByUserId(members, friend.getId()).get("level"));
    }

    @Test
    void quickMatchRandomMode_autoStartsWhenThirdMemberJoins() {
        User firstUser = persistUser("20263019", "random-full-first");
        User secondUser = persistUser("20263020", "random-full-second");
        User thirdUser = persistUser("20263021", "random-full-third");
        entityManager.flush();

        Map<String, Object> firstResponse = matchingRoomService.quickMatch("RANDOM", "KR", List.of(), firstUser);
        String roomId = (String) map(firstResponse.get("detail")).get("roomId");
        matchingRoomService.quickMatch("RANDOM", "KR", List.of(), secondUser);

        Map<String, Object> thirdResponse = matchingRoomService.quickMatch("RANDOM", "KR", List.of(), thirdUser);
        Map<String, Object> detail = map(thirdResponse.get("detail"));

        assertEquals(roomId, detail.get("roomId"));
        assertEquals("started", findRoom(roomId).getStatus());
        assertEquals("COMPLETED", detail.get("status"));
        assertEquals(3, detail.get("memberCount"));
        assertEquals("team-" + roomId.substring("room-".length()), thirdResponse.get("teamId"));
        assertTrue((Boolean) map(detail.get("actions")).get("chatEnabled"));
    }

    @Test
    void assertTeamRoomForCallAll_rejectsPersonalRoomWithCallAllMessage() {
        MatchingRoom room = MatchingRoom.create("개인방", 1);
        entityManager.persist(room);
        entityManager.flush();

        ApiException exception = assertThrows(
                ApiException.class,
                () -> matchingRoomService.assertTeamRoomForCallAll(room.getId())
        );

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatus());
        assertEquals("개인방에서는 전체 호출을 사용할 수 없습니다.", exception.getMessage());
    }

    @Test
    void start_setsSevenDayEndTimeForFeedbackReportGeneration() {
        User user = persistUser("20263014", "session-user");
        MatchingRoom room = MatchingRoom.create("일주일 투자방", 1);
        entityManager.persist(room);
        entityManager.persist(MatchingRoomMember.of(room, user));
        room.setMemberCount(1);
        entityManager.flush();
        Instant beforeStart = Instant.now();

        matchingRoomService.start("room-" + room.getId(), user);

        entityManager.flush();
        assertEquals("started", room.getStatus());
        Instant expectedEarliestEnd = beforeStart.plus(Duration.ofDays(7));
        Instant expectedLatestEnd = Instant.now().plus(Duration.ofDays(7));
        assertTrue(!room.getEndedAt().isBefore(expectedEarliestEnd));
        assertTrue(!room.getEndedAt().isAfter(expectedLatestEnd));
    }

    @Test
    void start_sendsChatRoomCreatedPushToAllRandomRoomMembersWhenWaitingRoomStarts() {
        User firstUser = persistUser("20263015", "random-first");
        User secondUser = persistUser("20263016", "random-second");
        MatchingRoom room = MatchingRoom.create("랜덤 매칭방", 3);
        room.setMatchType("RANDOM");
        entityManager.persist(room);
        entityManager.persist(MatchingRoomMember.of(room, firstUser));
        entityManager.persist(MatchingRoomMember.of(room, secondUser));
        room.setMemberCount(2);
        entityManager.flush();

        matchingRoomService.start("room-" + room.getId(), secondUser);

        verify(pushNotificationService).sendChatRoomCreated(room, List.of(firstUser.getId(), secondUser.getId()));
    }

    private User persistUser(String studentId, String nickname) {
        User user = User.builder()
                .studentId(studentId)
                .password("password")
                .nickname(nickname)
                .profileImageUrl("https://example.com/" + nickname + ".png")
                .build();
        entityManager.persist(user);
        return user;
    }

    private void persistLearningState(User user, int exp) {
        entityManager.persist(LearningUserStateEntity.builder()
                .userId(user.getId())
                .level(LearningProgressPolicy.fromExp(exp).level())
                .point(0)
                .exp(exp)
                .streakDays(0)
                .currentDayByCourseJson("{}")
                .completedDaysByCourseJson("{}")
                .submittedStepIdsJson("[]")
                .build());
    }

    private void persistAcceptedFriend(User requester, User addressee) {
        FriendRelation relation = FriendRelation.builder()
                .requesterUser(requester)
                .addresseeUser(addressee)
                .status("ACCEPTED")
                .build();
        friendRelationRepository.save(relation);
    }

    private MatchingRoom persistRoomWithMember(String name, String status, User user) {
        MatchingRoom room = MatchingRoom.create(name, 3);
        room.setStatus(status);
        entityManager.persist(room);
        entityManager.persist(MatchingRoomMember.of(room, user));
        room.setMemberCount(1);
        return room;
    }

    private void assertConfirmedMember(List<Map<String, Object>> members, Long userId, String role) {
        Map<String, Object> member = members.stream()
                .filter(candidate -> String.valueOf(userId).equals(String.valueOf(candidate.get("userId"))))
                .findFirst()
                .orElseThrow();
        assertEquals("CONFIRMED", member.get("status"));
        assertEquals(role, member.get("role"));
    }

    private Map<String, Object> memberByUserId(List<Map<String, Object>> members, Long userId) {
        return members.stream()
                .filter(candidate -> String.valueOf(userId).equals(String.valueOf(candidate.get("userId"))))
                .findFirst()
                .orElseThrow();
    }

    private MatchingRoom findRoom(String roomId) {
        Long id = Long.parseLong(roomId.substring("room-".length()));
        return matchingRoomRepository.findById(id).orElseThrow();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> map(Object value) {
        return (Map<String, Object>) value;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> list(Object value) {
        return (List<Map<String, Object>>) value;
    }
}

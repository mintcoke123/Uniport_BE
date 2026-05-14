package com.uniport.service;

import com.uniport.entity.FriendRelation;
import com.uniport.entity.MatchingRoom;
import com.uniport.entity.MatchingRoomMember;
import com.uniport.entity.User;
import com.uniport.exception.ApiException;
import com.uniport.repository.FriendRelationRepository;
import com.uniport.repository.MatchingRoomMemberRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

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

    private void persistAcceptedFriend(User requester, User addressee) {
        FriendRelation relation = FriendRelation.builder()
                .requesterUser(requester)
                .addresseeUser(addressee)
                .status("ACCEPTED")
                .build();
        friendRelationRepository.save(relation);
    }

    private void assertConfirmedMember(List<Map<String, Object>> members, Long userId, String role) {
        Map<String, Object> member = members.stream()
                .filter(candidate -> String.valueOf(userId).equals(String.valueOf(candidate.get("userId"))))
                .findFirst()
                .orElseThrow();
        assertEquals("CONFIRMED", member.get("status"));
        assertEquals(role, member.get("role"));
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

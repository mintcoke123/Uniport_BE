package com.uniport.scheduler;

import com.uniport.entity.MatchingRoom;
import com.uniport.entity.MatchingRoomMember;
import com.uniport.entity.User;
import com.uniport.repository.MatchingRoomRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:matchingroomendingschedulertest;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.sql.init.mode=never",
        "stock.master.import.enabled=false"
})
@Transactional
class MatchingRoomEndingSchedulerTest {

    @Autowired
    private MatchingRoomEndingScheduler scheduler;

    @Autowired
    private MatchingRoomRepository matchingRoomRepository;

    @Autowired
    private EntityManager entityManager;

    @MockitoBean
    private com.uniport.service.PushNotificationService pushNotificationService;

    @Test
    void runEndsStartedRoomsWhoseEndTimeHasPassedAndKeepsHistoricalUserTeamId() {
        User user = persistUser("20263021", "ended-room-user", "team-81");
        MatchingRoom room = MatchingRoom.builder()
                .name("종료 대상 방")
                .capacity(1)
                .memberCount(1)
                .status("started")
                .visibility("PUBLIC")
                .createdAt(Instant.now().minusSeconds(7200))
                .endedAt(Instant.now().minusSeconds(60))
                .build();
        entityManager.persist(room);
        entityManager.persist(MatchingRoomMember.of(room, user));
        entityManager.flush();

        scheduler.run();

        entityManager.flush();
        entityManager.clear();
        MatchingRoom endedRoom = matchingRoomRepository.findById(room.getId()).orElseThrow();
        User reloadedUser = entityManager.find(User.class, user.getId());
        assertEquals("ended", endedRoom.getStatus());
        assertEquals("team-81", reloadedUser.getTeamId());
    }

    @Test
    void runEndsLegacyStartedRoomsWithoutEndTimeAfterSevenDays() {
        Instant createdAt = Instant.now().minusSeconds(8 * 24 * 60 * 60);
        MatchingRoom room = MatchingRoom.builder()
                .name("레거시 종료 대상 방")
                .capacity(1)
                .memberCount(1)
                .status("started")
                .visibility("PUBLIC")
                .createdAt(createdAt)
                .endedAt(null)
                .build();
        entityManager.persist(room);
        entityManager.flush();

        scheduler.run();

        entityManager.flush();
        entityManager.clear();
        MatchingRoom endedRoom = matchingRoomRepository.findById(room.getId()).orElseThrow();
        assertEquals("ended", endedRoom.getStatus());
        assertEquals(createdAt.plusSeconds(7 * 24 * 60 * 60), endedRoom.getEndedAt());
    }

    private User persistUser(String studentId, String nickname, String teamId) {
        User user = User.builder()
                .studentId(studentId)
                .password("password")
                .nickname(nickname)
                .profileImageUrl("https://example.com/" + nickname + ".png")
                .teamId(teamId)
                .build();
        entityManager.persist(user);
        return user;
    }
}

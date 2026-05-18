package com.uniport.service;

import com.uniport.dto.FriendsDashboardResponseDTO;
import com.uniport.entity.FriendRelation;
import com.uniport.entity.LearningUserStateEntity;
import com.uniport.entity.User;
import com.uniport.entity.UserMyPagePreference;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:pointsocialfrienddashboardtest;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.sql.init.mode=never",
        "stock.master.import.enabled=false"
})
@Transactional
class PointSocialDataServiceFriendDashboardTest {

    @Autowired
    private PointSocialDataService pointSocialDataService;

    @Autowired
    private EntityManager entityManager;

    @Test
    void getFriendsDashboardRanksAllUsers() {
        User currentUser = persistUser("20261011", "현재사용자");
        User acceptedFriend = persistUser("20261012", "수락된친구");
        User nonFriend = persistUser("20261013", "친구아닌유저");
        User requestedUser = persistUser("20261014", "요청중유저");
        persistLearningState(currentUser.getId(), 1_000);
        persistLearningState(acceptedFriend.getId(), 4_000);
        persistLearningState(nonFriend.getId(), 8_000);
        persistLearningState(requestedUser.getId(), 6_000);
        persistRelation(currentUser, acceptedFriend, "ACCEPTED");
        persistRelation(currentUser, requestedUser, "REQUESTED");
        entityManager.flush();
        entityManager.clear();

        FriendsDashboardResponseDTO response = pointSocialDataService.getFriendsDashboard(currentUser);

        assertEquals(
                List.of("친구아닌유저", "요청중유저", "수락된친구", "현재사용자"),
                response.getRanking().getItems().stream()
                        .map(item -> item.getNickname())
                        .toList()
                        .subList(0, 4)
        );
        assertEquals(
                List.of(1, 2, 3, 4),
                response.getRanking().getItems().stream()
                        .map(item -> item.getRank())
                        .toList()
                        .subList(0, 4)
        );
        assertEquals(4, response.getMyRanking().getRank());
    }

    @Test
    void getFriendsDashboardReturnsWindowAroundMyRanking() {
        User currentUser = persistRankingUsers(30, 50);
        entityManager.flush();
        entityManager.clear();

        FriendsDashboardResponseDTO response = pointSocialDataService.getFriendsDashboard(currentUser);

        assertEquals(31, response.getRanking().getItems().size());
        assertEquals(15, response.getRanking().getItems().getFirst().getRank());
        assertEquals("랭킹15위", response.getRanking().getItems().getFirst().getNickname());
        assertEquals(30, response.getMyRanking().getRank());
        assertEquals(
                "랭킹30위",
                response.getRanking().getItems().stream()
                        .filter(item -> item.getRank() == 30)
                        .findFirst()
                        .orElseThrow()
                        .getNickname()
        );
        assertEquals(45, response.getRanking().getItems().getLast().getRank());
        assertEquals("랭킹45위", response.getRanking().getItems().getLast().getNickname());
    }

    @Test
    void getFriendsDashboardUsesCharacterProfileUrlInsteadOfLegacySocialUrl() {
        User currentUser = persistUser("20261201", "현재사용자");
        currentUser.setProfileImageUrl("https://lh3.googleusercontent.com/a/legacy=s96-c");
        persistPreference(currentUser.getId(), "PANDA");
        persistLearningState(currentUser.getId(), 1_000);
        entityManager.flush();
        entityManager.clear();

        FriendsDashboardResponseDTO response = pointSocialDataService.getFriendsDashboard(currentUser);

        String expectedProfileImageUrl = "https://uniportbe-production.up.railway.app/assets/mypage/profile-options/panda.png";
        assertEquals(expectedProfileImageUrl, response.getMyRanking().getProfileImageUrl());
        assertEquals(
                expectedProfileImageUrl,
                response.getRanking().getItems().stream()
                        .filter(item -> "현재사용자".equals(item.getNickname()))
                        .findFirst()
                        .orElseThrow()
                        .getProfileImageUrl()
        );
    }

    private User persistRankingUsers(int currentUserRank, int userCount) {
        User currentUser = persistUser("20261100", "랭킹" + currentUserRank + "위");
        persistLearningState(currentUser.getId(), (userCount - currentUserRank + 1) * 100);
        for (int rank = 1; rank <= userCount; rank++) {
            if (rank == currentUserRank) {
                continue;
            }
            User user = persistUser(
                    String.format("202611%02d", rank),
                    "랭킹" + rank + "위"
            );
            persistLearningState(user.getId(), (userCount - rank + 1) * 100);
            persistRelation(currentUser, user, "ACCEPTED");
        }
        return currentUser;
    }

    private User persistUser(String studentId, String nickname) {
        User user = User.builder()
                .studentId(studentId)
                .password("password")
                .nickname(nickname)
                .build();
        entityManager.persist(user);
        return user;
    }

    private void persistRelation(User requester, User addressee, String status) {
        FriendRelation relation = FriendRelation.builder()
                .requesterUser(requester)
                .addresseeUser(addressee)
                .status(status)
                .build();
        entityManager.persist(relation);
    }

    private void persistLearningState(Long userId, int exp) {
        LearningUserStateEntity state = LearningUserStateEntity.builder()
                .userId(userId)
                .level(1)
                .point(0)
                .exp(exp)
                .streakDays(0)
                .currentDayByCourseJson("{}")
                .completedDaysByCourseJson("{}")
                .submittedStepIdsJson("{}")
                .educationCurrentDayJson("{}")
                .educationCompletedDaysJson("{}")
                .educationQuizAnswersJson("{}")
                .educationCardProgressJson("{}")
                .educationSectorSelectionsJson("{}")
                .build();
        entityManager.persist(state);
    }

    private void persistPreference(Long userId, String selectedCharacterCode) {
        UserMyPagePreference preference = UserMyPagePreference.builder()
                .userId(userId)
                .selectedCharacterCode(selectedCharacterCode)
                .pushEnabled(true)
                .build();
        entityManager.persist(preference);
    }
}

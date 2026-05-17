package com.uniport.service;

import com.uniport.dto.FriendsDashboardResponseDTO;
import com.uniport.entity.LearningUserStateEntity;
import com.uniport.entity.User;
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
    void getFriendsDashboardRanksAllUsersNotOnlyAcceptedFriends() {
        User currentUser = persistUser("20261011", "현재사용자");
        User topUser = persistUser("20261012", "전체랭킹1위");
        User otherUser = persistUser("20261013", "전체랭킹2위");
        persistLearningState(currentUser.getId(), 1_000);
        persistLearningState(topUser.getId(), 4_000);
        persistLearningState(otherUser.getId(), 2_000);
        entityManager.flush();
        entityManager.clear();

        FriendsDashboardResponseDTO response = pointSocialDataService.getFriendsDashboard(currentUser);

        assertEquals(
                List.of("전체랭킹1위", "전체랭킹2위", "현재사용자"),
                response.getRanking().getItems().stream()
                        .map(item -> item.getNickname())
                        .limit(3)
                        .toList()
        );
        assertEquals(3, response.getMyRanking().getRank());
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

    private User persistRankingUsers(int currentUserRank, int userCount) {
        User currentUser = null;
        for (int rank = 1; rank <= userCount; rank++) {
            User user = persistUser(
                    String.format("202611%02d", rank),
                    "랭킹" + rank + "위"
            );
            persistLearningState(user.getId(), (userCount - rank + 1) * 100);
            if (rank == currentUserRank) {
                currentUser = user;
            }
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
}

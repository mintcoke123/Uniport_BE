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

package com.uniport.service;

import com.uniport.entity.FriendRelation;
import com.uniport.entity.User;
import com.uniport.exception.ApiException;
import com.uniport.repository.FriendRelationRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:pointsocialfriendremovaltest;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.sql.init.mode=never",
        "stock.master.import.enabled=false"
})
@Transactional
class PointSocialDataServiceFriendRemovalTest {

    @Autowired
    private PointSocialDataService pointSocialDataService;

    @Autowired
    private FriendRelationRepository friendRelationRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void deleteFriend_removesAcceptedRelationBetweenCurrentUserAndTargetUser() {
        User currentUser = persistUser("20261001", "current-friend-removal");
        User friend = persistUser("20261002", "target-friend-removal");
        FriendRelation relation = persistRelation(currentUser, friend, "ACCEPTED");
        entityManager.flush();
        entityManager.clear();

        pointSocialDataService.deleteFriend(currentUser, "USER_" + friend.getId());

        assertFalse(friendRelationRepository.findById(relation.getId()).isPresent());
    }

    @Test
    void deleteFriend_removesAcceptedRelationWhenCurrentUserIsAddressee() {
        User friend = persistUser("20261005", "requester-friend-removal");
        User currentUser = persistUser("20261006", "addressee-friend-removal");
        FriendRelation relation = persistRelation(friend, currentUser, "ACCEPTED");
        entityManager.flush();
        entityManager.clear();

        pointSocialDataService.deleteFriend(currentUser, "USER_" + friend.getId());

        assertFalse(friendRelationRepository.findById(relation.getId()).isPresent());
    }

    @Test
    void deleteFriend_rejectsUsersWithoutAcceptedRelation() {
        User currentUser = persistUser("20261003", "current-no-friend-removal");
        User target = persistUser("20261004", "target-no-friend-removal");
        persistRelation(currentUser, target, "REQUESTED");
        entityManager.flush();
        entityManager.clear();

        ApiException exception = assertThrows(
                ApiException.class,
                () -> pointSocialDataService.deleteFriend(currentUser, "USER_" + target.getId())
        );

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatus());
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

    private FriendRelation persistRelation(User requester, User addressee, String status) {
        FriendRelation relation = FriendRelation.builder()
                .requesterUser(requester)
                .addresseeUser(addressee)
                .status(status)
                .build();
        entityManager.persist(relation);
        return relation;
    }
}

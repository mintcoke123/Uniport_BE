package com.uniport.repository;

import com.uniport.entity.FriendRelation;
import com.uniport.entity.User;
import jakarta.persistence.EntityManager;
import org.hibernate.Hibernate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:friendrelationtest;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.sql.init.mode=never",
        "stock.master.import.enabled=false"
})
@Transactional
class FriendRelationRepositoryTest {

    @Autowired
    private FriendRelationRepository friendRelationRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void findByRequesterUserIdAndStatus_fetchesAddresseeUserForSentRequests() {
        User requester = persistUser("20260001", "requester");
        User addressee = persistUser("20260002", "addressee");
        persistRelation(requester, addressee, "REQUESTED");
        entityManager.flush();
        entityManager.clear();

        List<FriendRelation> relations = friendRelationRepository.findByRequesterUser_IdAndStatusOrderByCreatedAtDesc(
                requester.getId(),
                "REQUESTED"
        );

        assertEquals(1, relations.size());
        assertTrue(Hibernate.isInitialized(relations.getFirst().getAddresseeUser()));
    }

    @Test
    void findByAddresseeUserIdAndStatus_fetchesRequesterUserForReceivedRequests() {
        User requester = persistUser("20260003", "received-requester");
        User addressee = persistUser("20260004", "received-addressee");
        persistRelation(requester, addressee, "REQUESTED");
        entityManager.flush();
        entityManager.clear();

        List<FriendRelation> relations = friendRelationRepository.findByAddresseeUser_IdAndStatusOrderByCreatedAtDesc(
                addressee.getId(),
                "REQUESTED"
        );

        assertEquals(1, relations.size());
        assertTrue(Hibernate.isInitialized(relations.getFirst().getRequesterUser()));
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

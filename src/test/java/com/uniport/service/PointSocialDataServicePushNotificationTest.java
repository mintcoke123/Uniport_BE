package com.uniport.service;

import com.uniport.dto.FriendRequestCreateDTO;
import com.uniport.dto.FriendRequestDecisionDTO;
import com.uniport.dto.FriendRequestResponseDTO;
import com.uniport.entity.FriendRelation;
import com.uniport.entity.User;
import com.uniport.repository.FriendRelationRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:pointsocialpushnotificationtest;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.sql.init.mode=never",
        "stock.master.import.enabled=false"
})
@Transactional
class PointSocialDataServicePushNotificationTest {

    @Autowired
    private PointSocialDataService pointSocialDataService;

    @Autowired
    private FriendRelationRepository friendRelationRepository;

    @Autowired
    private EntityManager entityManager;

    @MockitoBean
    private PushNotificationService pushNotificationService;

    @Test
    void requestFriendSendsPushToTargetUser() {
        User requester = persistUser("20266001", "requester");
        User target = persistUser("20266002", "target");
        entityManager.flush();

        FriendRequestResponseDTO response = pointSocialDataService.requestFriend(
                requester,
                FriendRequestCreateDTO.builder()
                        .targetUserId("USER_" + target.getId())
                        .build()
        );

        verify(pushNotificationService).sendFriendRequestCreated(response.getRequestId(), requester, target);
    }

    @Test
    void requestFriend_allowsNewRequestAfterPreviousRejection() {
        User requester = persistUser("20266005", "rejected-requester");
        User target = persistUser("20266006", "rejected-target");
        FriendRelation relation = FriendRelation.builder()
                .requesterUser(requester)
                .addresseeUser(target)
                .status("REJECTED")
                .build();
        entityManager.persist(relation);
        entityManager.flush();
        entityManager.clear();

        FriendRequestResponseDTO response = pointSocialDataService.requestFriend(
                requester,
                FriendRequestCreateDTO.builder()
                        .targetUserId("USER_" + target.getId())
                        .build()
        );

        FriendRelation updated = friendRelationRepository.findById(relation.getId()).orElseThrow();
        assertEquals("REQ_" + relation.getId(), response.getRequestId());
        assertEquals("USER_" + target.getId(), response.getTargetUserId());
        assertEquals("REQUESTED", response.getStatus());
        assertEquals(requester.getId(), updated.getRequesterUser().getId());
        assertEquals(target.getId(), updated.getAddresseeUser().getId());
        assertEquals("REQUESTED", updated.getStatus());
        verify(pushNotificationService).sendFriendRequestCreated(response.getRequestId(), requester, target);
    }

    @Test
    void decideFriendRequestSendsPushToRequester() {
        User requester = persistUser("20266003", "requester");
        User addressee = persistUser("20266004", "addressee");
        FriendRelation relation = FriendRelation.builder()
                .requesterUser(requester)
                .addresseeUser(addressee)
                .status("REQUESTED")
                .build();
        entityManager.persist(relation);
        entityManager.flush();

        FriendRequestDecisionDTO decision = new FriendRequestDecisionDTO();
        decision.setAction("ACCEPT");

        pointSocialDataService.decideFriendRequest(
                addressee,
                "REQ_" + relation.getId(),
                decision
        );

        verify(pushNotificationService).sendFriendRequestDecision("REQ_" + relation.getId(), requester, addressee, "ACCEPTED");
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
}

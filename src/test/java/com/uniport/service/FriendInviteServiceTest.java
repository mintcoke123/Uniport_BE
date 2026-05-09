package com.uniport.service;

import com.uniport.dto.FriendInviteAcceptResponseDTO;
import com.uniport.dto.FriendInviteCreateResponseDTO;
import com.uniport.dto.FriendInviteDetailResponseDTO;
import com.uniport.entity.FriendInvite;
import com.uniport.entity.FriendRelation;
import com.uniport.entity.User;
import com.uniport.exception.ApiException;
import com.uniport.repository.FriendInviteRepository;
import com.uniport.repository.FriendRelationRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:friendinviteservicetest;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.sql.init.mode=never",
        "stock.master.import.enabled=false"
})
@TestPropertySource(properties = {
        "app.friend-invite.base-url=https://uniportbe-production.up.railway.app",
        "app.friend-invite.expiration-days=7"
})
@Transactional
class FriendInviteServiceTest {

    @Autowired
    private FriendInviteService friendInviteService;

    @Autowired
    private FriendInviteRepository friendInviteRepository;

    @Autowired
    private FriendRelationRepository friendRelationRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void createInvite_returnsActiveInviteWithShareUrlAndExpiration() {
        User inviter = persistUser("20262001", "inviter-create");
        entityManager.flush();

        FriendInviteCreateResponseDTO response = friendInviteService.createInvite(inviter);

        assertNotNull(response.getInviteCode());
        assertEquals(
                "https://uniportbe-production.up.railway.app/friend-invite?inviteCode=" + response.getInviteCode(),
                response.getInviteUrl()
        );
        assertNotNull(response.getExpiresAt());
        FriendInvite invite = friendInviteRepository.findByInviteCode(response.getInviteCode()).orElseThrow();
        assertEquals(inviter.getId(), invite.getInviterUser().getId());
        assertEquals("ACTIVE", invite.getStatus());
    }

    @Test
    void getInviteDetail_returnsInviterInformationForActiveInvite() {
        User inviter = persistUser("20262002", "inviter-detail");
        FriendInvite invite = persistInvite(inviter, "DETAIL123", "ACTIVE", LocalDateTime.now().plusDays(7));
        entityManager.flush();
        entityManager.clear();

        FriendInviteDetailResponseDTO response = friendInviteService.getInviteDetail(invite.getInviteCode());

        assertEquals("DETAIL123", response.getInviteCode());
        assertEquals("USER_" + inviter.getId(), response.getInviterUserId());
        assertEquals("inviter-detail", response.getInviterNickname());
        assertEquals("ACTIVE", response.getStatus());
    }

    @Test
    void acceptInvite_createsAcceptedFriendRelationBetweenInviterAndAccepter() {
        User inviter = persistUser("20262003", "inviter-accept");
        User accepter = persistUser("20262004", "accepter-accept");
        FriendInvite invite = persistInvite(inviter, "ACCEPT123", "ACTIVE", LocalDateTime.now().plusDays(7));
        entityManager.flush();
        entityManager.clear();

        FriendInviteAcceptResponseDTO response = friendInviteService.acceptInvite(accepter, invite.getInviteCode());

        assertEquals("USER_" + inviter.getId(), response.getFriendUserId());
        assertEquals("ACCEPTED", response.getStatus());
        FriendRelation relation = friendRelationRepository.findBetweenUsers(inviter.getId(), accepter.getId()).orElseThrow();
        assertEquals(inviter.getId(), relation.getRequesterUser().getId());
        assertEquals(accepter.getId(), relation.getAddresseeUser().getId());
        assertEquals("ACCEPTED", relation.getStatus());
        FriendInvite acceptedInvite = friendInviteRepository.findByInviteCode("ACCEPT123").orElseThrow();
        assertEquals("ACCEPTED", acceptedInvite.getStatus());
        assertEquals(accepter.getId(), acceptedInvite.getAcceptedByUser().getId());
        assertNotNull(acceptedInvite.getAcceptedAt());
    }

    @Test
    void acceptInvite_rejectsOwnInvite() {
        User inviter = persistUser("20262005", "inviter-self");
        FriendInvite invite = persistInvite(inviter, "SELF123", "ACTIVE", LocalDateTime.now().plusDays(7));
        entityManager.flush();
        entityManager.clear();

        ApiException exception = assertThrows(
                ApiException.class,
                () -> friendInviteService.acceptInvite(inviter, invite.getInviteCode())
        );

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatus());
    }

    @Test
    void acceptInvite_rejectsAlreadyFriends() {
        User inviter = persistUser("20262006", "inviter-conflict");
        User accepter = persistUser("20262007", "accepter-conflict");
        persistRelation(inviter, accepter, "ACCEPTED");
        FriendInvite invite = persistInvite(inviter, "FRIEND123", "ACTIVE", LocalDateTime.now().plusDays(7));
        entityManager.flush();
        entityManager.clear();

        ApiException exception = assertThrows(
                ApiException.class,
                () -> friendInviteService.acceptInvite(accepter, invite.getInviteCode())
        );

        assertEquals(HttpStatus.CONFLICT, exception.getStatus());
    }

    @Test
    void getInviteDetail_returnsGoneForExpiredInvite() {
        User inviter = persistUser("20262008", "inviter-expired");
        FriendInvite invite = persistInvite(inviter, "EXPIRED123", "ACTIVE", LocalDateTime.now().minusMinutes(1));
        entityManager.flush();
        entityManager.clear();

        ApiException exception = assertThrows(
                ApiException.class,
                () -> friendInviteService.getInviteDetail(invite.getInviteCode())
        );

        assertEquals(HttpStatus.GONE, exception.getStatus());
        assertTrue(friendInviteRepository.findByInviteCode("EXPIRED123").orElseThrow().getStatus().equals("EXPIRED"));
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

    private FriendInvite persistInvite(User inviter, String inviteCode, String status, LocalDateTime expiresAt) {
        FriendInvite invite = FriendInvite.builder()
                .inviterUser(inviter)
                .inviteCode(inviteCode)
                .status(status)
                .expiresAt(expiresAt)
                .build();
        entityManager.persist(invite);
        return invite;
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

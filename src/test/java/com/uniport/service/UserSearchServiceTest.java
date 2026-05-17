package com.uniport.service;

import com.uniport.dto.UserSearchItemDTO;
import com.uniport.entity.FriendRelation;
import com.uniport.entity.User;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:usersearchservicetest;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.sql.init.mode=never",
        "stock.master.import.enabled=false"
})
@Transactional
class UserSearchServiceTest {

    @Autowired
    private UserSearchService userSearchService;

    @Autowired
    private EntityManager entityManager;

    @Test
    void searchUsesFriendRelationStatusForAlreadyMatchedAndAlreadyInvited() {
        User currentUser = persistUser("20262001", "현재사용자", null);
        User teamOnlyUser = persistUser("20262002", "팀만있는유저", "team-1");
        User acceptedFriend = persistUser("20262003", "수락된친구", null);
        User sentRequestUser = persistUser("20262004", "보낸요청유저", null);
        User receivedRequestUser = persistUser("20262005", "받은요청유저", null);
        persistRelation(currentUser, acceptedFriend, "ACCEPTED");
        persistRelation(currentUser, sentRequestUser, "REQUESTED");
        persistRelation(receivedRequestUser, currentUser, "REQUESTED");
        entityManager.flush();
        entityManager.clear();

        List<UserSearchItemDTO> result = userSearchService.search(currentUser, "", 20);

        Map<String, UserSearchItemDTO> byNickname = result.stream()
                .collect(Collectors.toMap(UserSearchItemDTO::getNickname, Function.identity()));
        assertFalse(byNickname.get("팀만있는유저").isAlreadyMatched());
        assertFalse(byNickname.get("팀만있는유저").isAlreadyInvited());
        assertTrue(byNickname.get("수락된친구").isAlreadyMatched());
        assertFalse(byNickname.get("수락된친구").isAlreadyInvited());
        assertFalse(byNickname.get("보낸요청유저").isAlreadyMatched());
        assertTrue(byNickname.get("보낸요청유저").isAlreadyInvited());
        assertFalse(byNickname.get("받은요청유저").isAlreadyMatched());
        assertTrue(byNickname.get("받은요청유저").isAlreadyInvited());
    }

    private User persistUser(String studentId, String nickname, String teamId) {
        User user = User.builder()
                .studentId(studentId)
                .password("password")
                .nickname(nickname)
                .teamId(teamId)
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
}

package com.uniport.service;

import com.uniport.repository.CompetitionApplicationRepository;
import com.uniport.repository.FriendInviteRepository;
import com.uniport.repository.FriendRelationRepository;
import com.uniport.repository.HoldingRepository;
import com.uniport.repository.LearningUserStateRepository;
import com.uniport.repository.MatchingRoomMemberRepository;
import com.uniport.repository.OrderRepository;
import com.uniport.repository.PointShopOrderRepository;
import com.uniport.repository.PointTransactionRepository;
import com.uniport.repository.PointWalletRepository;
import com.uniport.repository.UserAuthIdentityRepository;
import com.uniport.repository.UserMyPagePreferenceRepository;
import com.uniport.repository.UserPushTokenRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.inOrder;

@ExtendWith(MockitoExtension.class)
class UserDeletionReferenceCleanupServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private HoldingRepository holdingRepository;

    @Mock
    private MatchingRoomMemberRepository matchingRoomMemberRepository;

    @Mock
    private FriendInviteRepository friendInviteRepository;

    @Mock
    private FriendRelationRepository friendRelationRepository;

    @Mock
    private PointShopOrderRepository pointShopOrderRepository;

    @Mock
    private PointTransactionRepository pointTransactionRepository;

    @Mock
    private PointWalletRepository pointWalletRepository;

    @Mock
    private UserAuthIdentityRepository userAuthIdentityRepository;

    @Mock
    private UserMyPagePreferenceRepository userMyPagePreferenceRepository;

    @Mock
    private UserPushTokenRepository userPushTokenRepository;

    @Mock
    private LearningUserStateRepository learningUserStateRepository;

    @Mock
    private CompetitionApplicationRepository competitionApplicationRepository;

    @InjectMocks
    private UserDeletionReferenceCleanupService cleanupService;

    @Test
    void cleanupUserReferences_deletesKnownUserReferencesBeforeUserDeletion() {
        cleanupService.cleanupUserReferences(465L);

        InOrder order = inOrder(
                pointShopOrderRepository,
                pointTransactionRepository,
                pointWalletRepository,
                orderRepository,
                holdingRepository,
                matchingRoomMemberRepository,
                friendInviteRepository,
                friendRelationRepository,
                userAuthIdentityRepository,
                userPushTokenRepository,
                userMyPagePreferenceRepository,
                learningUserStateRepository,
                competitionApplicationRepository
        );
        order.verify(pointShopOrderRepository).deleteByUser_Id(465L);
        order.verify(pointTransactionRepository).deleteByUser_Id(465L);
        order.verify(pointWalletRepository).deleteByUser_Id(465L);
        order.verify(orderRepository).deleteByUser_Id(465L);
        order.verify(holdingRepository).deleteByUser_Id(465L);
        order.verify(matchingRoomMemberRepository).deleteAllByUserId(465L);
        order.verify(friendInviteRepository).deleteAllByUserId(465L);
        order.verify(friendRelationRepository).deleteByRequesterUser_IdOrAddresseeUser_Id(465L, 465L);
        order.verify(userAuthIdentityRepository).deleteByUser_Id(465L);
        order.verify(userPushTokenRepository).deleteByUser_Id(465L);
        order.verify(userMyPagePreferenceRepository).deleteById(465L);
        order.verify(learningUserStateRepository).deleteById(465L);
        order.verify(competitionApplicationRepository).deleteByUser_Id(465L);
    }
}

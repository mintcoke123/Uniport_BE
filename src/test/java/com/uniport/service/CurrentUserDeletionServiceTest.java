package com.uniport.service;

import com.uniport.entity.User;
import com.uniport.repository.FriendInviteRepository;
import com.uniport.repository.FriendRelationRepository;
import com.uniport.repository.HoldingRepository;
import com.uniport.repository.LearningUserStateRepository;
import com.uniport.repository.MatchingRoomMemberRepository;
import com.uniport.repository.OrderRepository;
import com.uniport.repository.PointShopOrderRepository;
import com.uniport.repository.PointTransactionRepository;
import com.uniport.repository.PointWalletRepository;
import com.uniport.repository.UserMyPagePreferenceRepository;
import com.uniport.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.inOrder;

@ExtendWith(MockitoExtension.class)
class CurrentUserDeletionServiceTest {

    @Mock
    private UserRepository userRepository;

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
    private UserMyPagePreferenceRepository userMyPagePreferenceRepository;

    @Mock
    private LearningUserStateRepository learningUserStateRepository;

    @InjectMocks
    private CurrentUserDeletionService currentUserDeletionService;

    @Test
    void deleteCurrentUser_cleansFriendInvitesBeforeDeletingUser() {
        User user = User.builder().id(12L).build();

        currentUserDeletionService.deleteCurrentUser(user);

        InOrder order = inOrder(
                pointShopOrderRepository,
                pointTransactionRepository,
                pointWalletRepository,
                orderRepository,
                holdingRepository,
                matchingRoomMemberRepository,
                friendInviteRepository,
                friendRelationRepository,
                userMyPagePreferenceRepository,
                learningUserStateRepository,
                userRepository
        );
        order.verify(pointShopOrderRepository).deleteByUser_Id(12L);
        order.verify(pointTransactionRepository).deleteByUser_Id(12L);
        order.verify(pointWalletRepository).deleteByUser_Id(12L);
        order.verify(orderRepository).deleteByUser_Id(12L);
        order.verify(holdingRepository).deleteByUser_Id(12L);
        order.verify(matchingRoomMemberRepository).deleteAllByUserId(12L);
        order.verify(friendInviteRepository).deleteAllByUserId(12L);
        order.verify(friendRelationRepository).deleteByRequesterUser_IdOrAddresseeUser_Id(12L, 12L);
        order.verify(userMyPagePreferenceRepository).deleteById(12L);
        order.verify(learningUserStateRepository).deleteById(12L);
        order.verify(userRepository).delete(user);
    }
}

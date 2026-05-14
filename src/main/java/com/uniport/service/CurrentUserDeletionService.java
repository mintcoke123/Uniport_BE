package com.uniport.service;

import com.uniport.entity.User;
import com.uniport.exception.ApiException;
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
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CurrentUserDeletionService {

    private final UserRepository userRepository;
    private final OrderRepository orderRepository;
    private final HoldingRepository holdingRepository;
    private final MatchingRoomMemberRepository matchingRoomMemberRepository;
    private final FriendInviteRepository friendInviteRepository;
    private final FriendRelationRepository friendRelationRepository;
    private final PointShopOrderRepository pointShopOrderRepository;
    private final PointTransactionRepository pointTransactionRepository;
    private final PointWalletRepository pointWalletRepository;
    private final UserMyPagePreferenceRepository userMyPagePreferenceRepository;
    private final LearningUserStateRepository learningUserStateRepository;

    public CurrentUserDeletionService(
            UserRepository userRepository,
            OrderRepository orderRepository,
            HoldingRepository holdingRepository,
            MatchingRoomMemberRepository matchingRoomMemberRepository,
            FriendInviteRepository friendInviteRepository,
            FriendRelationRepository friendRelationRepository,
            PointShopOrderRepository pointShopOrderRepository,
            PointTransactionRepository pointTransactionRepository,
            PointWalletRepository pointWalletRepository,
            UserMyPagePreferenceRepository userMyPagePreferenceRepository,
            LearningUserStateRepository learningUserStateRepository) {
        this.userRepository = userRepository;
        this.orderRepository = orderRepository;
        this.holdingRepository = holdingRepository;
        this.matchingRoomMemberRepository = matchingRoomMemberRepository;
        this.friendInviteRepository = friendInviteRepository;
        this.friendRelationRepository = friendRelationRepository;
        this.pointShopOrderRepository = pointShopOrderRepository;
        this.pointTransactionRepository = pointTransactionRepository;
        this.pointWalletRepository = pointWalletRepository;
        this.userMyPagePreferenceRepository = userMyPagePreferenceRepository;
        this.learningUserStateRepository = learningUserStateRepository;
    }

    @Transactional
    public void deleteCurrentUser(User user) {
        if (user == null || user.getId() == null) {
            throw new ApiException("Authentication is required", HttpStatus.UNAUTHORIZED);
        }

        Long userId = user.getId();
        pointShopOrderRepository.deleteByUser_Id(userId);
        pointTransactionRepository.deleteByUser_Id(userId);
        pointWalletRepository.deleteByUser_Id(userId);
        orderRepository.deleteByUser_Id(userId);
        holdingRepository.deleteByUser_Id(userId);
        matchingRoomMemberRepository.deleteAllByUserId(userId);
        friendInviteRepository.deleteAllByUserId(userId);
        friendRelationRepository.deleteByRequesterUser_IdOrAddresseeUser_Id(userId, userId);
        userMyPagePreferenceRepository.deleteById(userId);
        learningUserStateRepository.deleteById(userId);
        userRepository.delete(user);
    }
}

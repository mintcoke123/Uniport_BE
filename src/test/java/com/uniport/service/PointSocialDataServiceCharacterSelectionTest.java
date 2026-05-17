package com.uniport.service;

import com.uniport.dto.MyPageCharacterSelectRequestDTO;
import com.uniport.dto.MyPageResponseDTO;
import com.uniport.entity.User;
import com.uniport.entity.UserMyPagePreference;
import com.uniport.repository.FriendRelationRepository;
import com.uniport.repository.GifticonInventoryRepository;
import com.uniport.repository.HoldingRepository;
import com.uniport.repository.LearningUserStateRepository;
import com.uniport.repository.OrderRepository;
import com.uniport.repository.PointShopOrderRepository;
import com.uniport.repository.PointShopProductRepository;
import com.uniport.repository.PointTransactionRepository;
import com.uniport.repository.PointWalletRepository;
import com.uniport.repository.UserMyPagePreferenceRepository;
import com.uniport.repository.UserRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PointSocialDataServiceCharacterSelectionTest {

    @Test
    void selectCharacterUpdatesProfileImageUrlForSharedAvatarConsumers() {
        Fixture fixture = new Fixture();
        User user = User.builder()
                .id(7L)
                .nickname("고윤서")
                .profileImageUrl("https://example.com/firebase.png")
                .build();
        UserMyPagePreference preference = UserMyPagePreference.builder()
                .userId(7L)
                .selectedCharacterCode("SEED")
                .pushEnabled(true)
                .build();
        MyPageCharacterSelectRequestDTO request = MyPageCharacterSelectRequestDTO.builder()
                .characterCode("FOX")
                .build();

        when(fixture.userMyPagePreferenceRepository.findById(7L)).thenReturn(Optional.of(preference));
        when(fixture.userRepository.findById(7L)).thenReturn(Optional.of(user));
        when(fixture.pointWalletRepository.findByUser_Id(7L)).thenReturn(Optional.empty());
        when(fixture.pointShopOrderRepository.findByUser_IdOrderByCreatedAtDesc(7L)).thenReturn(List.of());
        when(fixture.friendRelationRepository.findByRequesterUser_IdOrAddresseeUser_IdOrderByUpdatedAtDesc(7L, 7L))
                .thenReturn(List.of());
        when(fixture.orderRepository.findByUser_IdOrderByOrderDateDesc(7L)).thenReturn(List.of());
        when(fixture.holdingRepository.findByUser_Id(7L)).thenReturn(List.of());
        when(fixture.learningUserStateRepository.findById(7L)).thenReturn(Optional.empty());

        MyPageResponseDTO response = fixture.service.selectCharacter(user, request);

        String expectedProfileImageUrl = "https://uniportbe-production.up.railway.app/assets/mypage/profile-options/fox.png";
        assertEquals("FOX", preference.getSelectedCharacterCode());
        assertEquals(expectedProfileImageUrl, user.getProfileImageUrl());
        assertEquals(expectedProfileImageUrl, response.getUser().getProfileImageUrl());
        verify(fixture.userRepository).save(user);
    }

    private static class Fixture {
        final PointWalletRepository pointWalletRepository = mock(PointWalletRepository.class);
        final PointTransactionRepository pointTransactionRepository = mock(PointTransactionRepository.class);
        final PointShopProductRepository pointShopProductRepository = mock(PointShopProductRepository.class);
        final GifticonInventoryRepository gifticonInventoryRepository = mock(GifticonInventoryRepository.class);
        final PointShopOrderRepository pointShopOrderRepository = mock(PointShopOrderRepository.class);
        final FriendRelationRepository friendRelationRepository = mock(FriendRelationRepository.class);
        final UserRepository userRepository = mock(UserRepository.class);
        final UserMyPagePreferenceRepository userMyPagePreferenceRepository = mock(UserMyPagePreferenceRepository.class);
        final HoldingRepository holdingRepository = mock(HoldingRepository.class);
        final OrderRepository orderRepository = mock(OrderRepository.class);
        final LearningUserStateRepository learningUserStateRepository = mock(LearningUserStateRepository.class);
        final PushNotificationService pushNotificationService = mock(PushNotificationService.class);
        final PointLedgerService pointLedgerService = mock(PointLedgerService.class);
        final PointSocialDataService service = new PointSocialDataService(
                pointWalletRepository,
                pointTransactionRepository,
                pointShopProductRepository,
                gifticonInventoryRepository,
                pointShopOrderRepository,
                friendRelationRepository,
                userRepository,
                userMyPagePreferenceRepository,
                holdingRepository,
                orderRepository,
                learningUserStateRepository,
                pushNotificationService,
                pointLedgerService,
                new ProfileImageUrlService()
        );
    }
}

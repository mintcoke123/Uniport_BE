package com.uniport.service;

import com.uniport.dto.MyPageCharacterCardDTO;
import com.uniport.dto.MyPageResponseDTO;
import com.uniport.entity.LearningUserStateEntity;
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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PointSocialDataServiceRewardPlanTest {

    @Test
    void myPageUsesRewardPlanLevelCapAndSelectedCharacterStage() {
        PointWalletRepository pointWalletRepository = mock(PointWalletRepository.class);
        PointTransactionRepository pointTransactionRepository = mock(PointTransactionRepository.class);
        PointShopProductRepository pointShopProductRepository = mock(PointShopProductRepository.class);
        GifticonInventoryRepository gifticonInventoryRepository = mock(GifticonInventoryRepository.class);
        PointShopOrderRepository pointShopOrderRepository = mock(PointShopOrderRepository.class);
        FriendRelationRepository friendRelationRepository = mock(FriendRelationRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        UserMyPagePreferenceRepository userMyPagePreferenceRepository = mock(UserMyPagePreferenceRepository.class);
        HoldingRepository holdingRepository = mock(HoldingRepository.class);
        OrderRepository orderRepository = mock(OrderRepository.class);
        LearningUserStateRepository learningUserStateRepository = mock(LearningUserStateRepository.class);
        PushNotificationService pushNotificationService = mock(PushNotificationService.class);
        PointLedgerService pointLedgerService = mock(PointLedgerService.class);
        PointSocialDataService service = new PointSocialDataService(
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
        User user = User.builder().id(7L).nickname("고윤서").investmentProfileResult("균형 투자형").build();
        UserMyPagePreference preference = UserMyPagePreference.builder()
                .userId(7L)
                .selectedCharacterCode("PANDA")
                .pushEnabled(true)
                .build();
        LearningUserStateEntity learningState = LearningUserStateEntity.builder()
                .userId(7L)
                .level(1)
                .point(0)
                .exp(40_000)
                .streakDays(3)
                .currentDayByCourseJson("{}")
                .completedDaysByCourseJson("{}")
                .educationCurrentDayJson("{}")
                .educationCompletedDaysJson("{}")
                .educationCardProgressJson("{}")
                .educationQuizAnswersJson("{}")
                .educationSectorSelectionsJson("{}")
                .build();

        when(userMyPagePreferenceRepository.findById(7L)).thenReturn(Optional.of(preference));
        when(pointShopOrderRepository.findByUser_IdOrderByCreatedAtDesc(7L)).thenReturn(List.of());
        when(friendRelationRepository.findByRequesterUser_IdOrAddresseeUser_IdOrderByUpdatedAtDesc(7L, 7L)).thenReturn(List.of());
        when(orderRepository.findByUser_IdOrderByOrderDateDesc(7L)).thenReturn(List.of());
        when(holdingRepository.findByUser_Id(7L)).thenReturn(List.of());
        when(learningUserStateRepository.findById(7L)).thenReturn(Optional.of(learningState));

        MyPageResponseDTO response = service.getMyPage(user);

        assertEquals(100, response.getUser().getLevel());
        assertEquals(300, response.getExp().getCurrentExp());
        assertEquals(300, response.getExp().getMaxExp());
        assertEquals(40_000, response.getExp().getTotalExp());
        assertEquals(100, response.getExp().getMaxLevel());
        assertEquals("PANDA", response.getUser().getCharacterCode());
        assertEquals(3, response.getUser().getCharacterStage());
        assertEquals("character_panda_stage_3", response.getUser().getCharacterAssetKey());
        MyPageCharacterCardDTO selected = response.getCharacters().stream()
                .filter(character -> Boolean.TRUE.equals(character.getSelected()))
                .findFirst()
                .orElseThrow();
        assertEquals("PANDA", selected.getCode());
        assertEquals(3, selected.getStage());
        assertEquals("character_panda_stage_3", selected.getAssetKey());
        assertEquals(8, response.getCharacters().size());
        assertEquals(
                List.of("SEED", "PANDA", "DOLPHIN", "RESEARCHER", "FOX", "FARMER", "OWL", "SURFER"),
                response.getCharacters().stream().map(MyPageCharacterCardDTO::getCode).toList()
        );
        assertTrue(response.getCharacters().stream().allMatch(character -> character.getAssetKey() != null));
    }
}

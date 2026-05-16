package com.uniport.service;

import com.uniport.dto.PointShopProductsResponseDTO;
import com.uniport.entity.PointShopProduct;
import com.uniport.entity.PointWallet;
import com.uniport.entity.User;
import com.uniport.exception.ApiException;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PointSocialDataServicePointShopPrdTest {

    @Test
    void pointShopProductsReturnPrdListShapeWithBalanceCategoriesAndStock() {
        Fixture fixture = new Fixture();
        User user = User.builder().id(7L).nickname("고윤서").build();
        PointShopProduct coffee = product(1L, "스타벅스", "아이스 아메리카노 Tall", "CAFE", 5_000, "ACTIVE", 1);
        PointShopProduct cu = product(2L, "CU", "모바일 금액권 1,000원", "CONVENIENCE", 1_500, "ACTIVE", 2);

        when(fixture.pointShopProductRepository.findAllByOrderBySortOrderAscCreatedAtDesc()).thenReturn(List.of(coffee, cu));
        when(fixture.pointWalletRepository.findByUser_Id(7L)).thenReturn(java.util.Optional.of(
                PointWallet.builder().user(user).balance(8_200).build()
        ));
        when(fixture.gifticonInventoryRepository.countByProduct_IdAndStatus(1L, "AVAILABLE")).thenReturn(12L);
        when(fixture.gifticonInventoryRepository.countByProduct_IdAndStatus(2L, "AVAILABLE")).thenReturn(3L);

        PointShopProductsResponseDTO response = fixture.service.getPointShopProducts(user, "커피");

        assertEquals(8_200, response.getMyPoint());
        assertEquals(List.of("전체", "커피", "편의점"), response.getCategories());
        assertEquals(1, response.getProducts().size());
        assertEquals("product-1", response.getProducts().get(0).getId());
        assertEquals("커피", response.getProducts().get(0).getCategory());
        assertEquals(5_000, response.getProducts().get(0).getPricePoint());
        assertEquals("ACTIVE", response.getProducts().get(0).getStatus());
        assertEquals(12, response.getProducts().get(0).getStockCount());
    }

    @Test
    void createPointShopOrderIsBlockedUntilRealGifticonOperationStarts() {
        Fixture fixture = new Fixture();
        User user = User.builder().id(7L).nickname("고윤서").build();

        ApiException exception = assertThrows(
                ApiException.class,
                () -> fixture.service.createPointShopOrder(user, "product-1")
        );

        assertEquals(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE, exception.getStatus());
        assertEquals("포인트샵 교환은 실제 기프티콘 운영 준비 후 열릴 예정이에요.", exception.getMessage());
        verifyNoInteractions(
                fixture.pointShopProductRepository,
                fixture.gifticonInventoryRepository,
                fixture.pointShopOrderRepository,
                fixture.pointLedgerService
        );
    }

    private static PointShopProduct product(Long id, String brand, String name, String category, int point, String status, int sortOrder) {
        return PointShopProduct.builder()
                .id(id)
                .brand(brand)
                .name(name)
                .category(category)
                .pricePoint(point)
                .status(status)
                .sortOrder(sortOrder)
                .build();
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
                pointLedgerService
        );
    }
}

package com.uniport.controller;

import com.uniport.repository.FriendRelationRepository;
import com.uniport.repository.GifticonInventoryRepository;
import com.uniport.repository.ManagedCommunityCommentRepository;
import com.uniport.repository.ManagedCommunityPostRepository;
import com.uniport.repository.ManagedEtfRepository;
import com.uniport.repository.ManagedGroupInsightRepository;
import com.uniport.repository.ManagedNewsArticleRepository;
import com.uniport.repository.MatchingRoomMemberRepository;
import com.uniport.repository.PointShopOrderRepository;
import com.uniport.repository.PointShopProductRepository;
import com.uniport.repository.PointTransactionRepository;
import com.uniport.repository.PointWalletRepository;
import com.uniport.repository.UserRepository;
import com.uniport.service.CompetitionService;
import com.uniport.service.EducationContentService;
import com.uniport.service.MatchingRoomService;
import com.uniport.service.UserAccountDeletionService;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class AdminConsoleControllerDeleteUserTest {

    @Test
    void deleteUserByAdminConsole_deletesUserAccountById() {
        UserAccountDeletionService userAccountDeletionService = mock(UserAccountDeletionService.class);
        UserRepository userRepository = mock(UserRepository.class);
        AdminConsoleController controller = new AdminConsoleController(
                mock(ManagedEtfRepository.class),
                mock(ManagedNewsArticleRepository.class),
                mock(ManagedCommunityPostRepository.class),
                mock(ManagedCommunityCommentRepository.class),
                mock(ManagedGroupInsightRepository.class),
                mock(PointWalletRepository.class),
                mock(PointTransactionRepository.class),
                mock(PointShopProductRepository.class),
                mock(GifticonInventoryRepository.class),
                mock(PointShopOrderRepository.class),
                mock(MatchingRoomMemberRepository.class),
                mock(FriendRelationRepository.class),
                userAccountDeletionService,
                userRepository,
                mock(CompetitionService.class),
                mock(EducationContentService.class),
                mock(MatchingRoomService.class)
        );

        controller.deleteUserByAdminConsole(467L);

        verify(userAccountDeletionService).deleteUserById(467L);
    }
}

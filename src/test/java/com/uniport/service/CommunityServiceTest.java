package com.uniport.service;

import com.uniport.repository.ManagedCommunityCommentRepository;
import com.uniport.repository.ManagedCommunityPostLikeRepository;
import com.uniport.repository.ManagedCommunityPostRepository;
import com.uniport.repository.ManagedCommunityReportRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CommunityServiceTest {

    @Mock
    private ManagedCommunityPostRepository managedCommunityPostRepository;

    @Mock
    private ManagedCommunityCommentRepository managedCommunityCommentRepository;

    @Mock
    private ManagedCommunityPostLikeRepository managedCommunityPostLikeRepository;

    @Mock
    private ManagedCommunityReportRepository managedCommunityReportRepository;

    @Mock
    private StockVisualAssetResolver stockVisualAssetResolver;

    private CommunityService communityService;

    @BeforeEach
    void setUp() {
        communityService = new CommunityService(
                managedCommunityPostRepository,
                managedCommunityCommentRepository,
                managedCommunityPostLikeRepository,
                managedCommunityReportRepository,
                stockVisualAssetResolver
        );
    }

    @Test
    void getPostsNormalizesOptionalFiltersBeforeRepositorySearch() {
        when(managedCommunityPostRepository.search("GENERAL", "ABCDEF", "BULLISH"))
                .thenReturn(List.of());

        communityService.getPosts(null, "latest", "general", "abcdef", "bullish", null, 20);

        verify(managedCommunityPostRepository).search("GENERAL", "ABCDEF", "BULLISH");
    }
}

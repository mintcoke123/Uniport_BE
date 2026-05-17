package com.uniport.service;

import com.uniport.dto.CommunityAuthorDTO;
import com.uniport.dto.CommunityCommentCreateRequestDTO;
import com.uniport.dto.CommunityCommentItemDTO;
import com.uniport.dto.CommunityCommentMutationResponseDTO;
import com.uniport.dto.CommunityCommentsResponseDTO;
import com.uniport.dto.CommunityLikeResponseDTO;
import com.uniport.dto.CommunityPostCreateRequestDTO;
import com.uniport.dto.CommunityPostDetailDTO;
import com.uniport.dto.CommunityPostMutationResponseDTO;
import com.uniport.dto.CommunityPostSummaryDTO;
import com.uniport.dto.CommunityPostUpdateRequestDTO;
import com.uniport.dto.CommunityPostsResponseDTO;
import com.uniport.dto.CommunityReportRequestDTO;
import com.uniport.dto.CommunityReportResponseDTO;
import com.uniport.dto.InvestorSentimentDTO;
import com.uniport.dto.StockVisualDTO;
import com.uniport.entity.ManagedCommunityComment;
import com.uniport.entity.ManagedCommunityPost;
import com.uniport.entity.ManagedCommunityPostLike;
import com.uniport.entity.ManagedCommunityReport;
import com.uniport.entity.User;
import com.uniport.exception.ApiException;
import com.uniport.repository.ManagedCommunityCommentRepository;
import com.uniport.repository.ManagedCommunityPostLikeRepository;
import com.uniport.repository.ManagedCommunityPostRepository;
import com.uniport.repository.ManagedCommunityReportRepository;
import com.uniport.repository.UserMyPagePreferenceRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class CommunityService {

    private static final Set<String> POST_TYPES = Set.of("GENERAL", "ACHIEVEMENT", "ANALYSIS_SHARE");
    private static final Set<String> FEED_SORTS = Set.of("LATEST", "HOT");
    private static final Set<String> REPORT_REASONS = Set.of("SPAM", "ABUSE", "HATE", "ADULT", "OTHER");
    private static final Set<String> SENTIMENTS = Set.of("BULLISH", "BEARISH", "NEUTRAL");

    private final ManagedCommunityPostRepository managedCommunityPostRepository;
    private final ManagedCommunityCommentRepository managedCommunityCommentRepository;
    private final ManagedCommunityPostLikeRepository managedCommunityPostLikeRepository;
    private final ManagedCommunityReportRepository managedCommunityReportRepository;
    private final StockVisualAssetResolver stockVisualAssetResolver;
    private final UserMyPagePreferenceRepository userMyPagePreferenceRepository;
    private final ProfileImageUrlService profileImageUrlService;

    public CommunityService(ManagedCommunityPostRepository managedCommunityPostRepository,
                            ManagedCommunityCommentRepository managedCommunityCommentRepository,
                            ManagedCommunityPostLikeRepository managedCommunityPostLikeRepository,
                            ManagedCommunityReportRepository managedCommunityReportRepository,
                            StockVisualAssetResolver stockVisualAssetResolver,
                            UserMyPagePreferenceRepository userMyPagePreferenceRepository,
                            ProfileImageUrlService profileImageUrlService) {
        this.managedCommunityPostRepository = managedCommunityPostRepository;
        this.managedCommunityCommentRepository = managedCommunityCommentRepository;
        this.managedCommunityPostLikeRepository = managedCommunityPostLikeRepository;
        this.managedCommunityReportRepository = managedCommunityReportRepository;
        this.stockVisualAssetResolver = stockVisualAssetResolver;
        this.userMyPagePreferenceRepository = userMyPagePreferenceRepository;
        this.profileImageUrlService = profileImageUrlService;
    }

    @Transactional(readOnly = true)
    public CommunityPostsResponseDTO getPosts(User viewer, String sort, String type, String stockCode, String sentiment, String cursor, Integer size) {
        String safeSort = normalizeSort(sort);
        String safeType = normalizeType(type, false);
        String safeStockCode = normalizeStockCode(stockCode);
        String safeSentiment = normalizeSentiment(sentiment, false);
        String typeFilter = safeType == null ? null : safeType.toUpperCase(Locale.ROOT);
        String stockCodeFilter = safeStockCode == null ? null : safeStockCode.toUpperCase(Locale.ROOT);
        String sentimentFilter = safeSentiment == null ? null : safeSentiment.toUpperCase(Locale.ROOT);
        int safeSize = size == null || size < 1 ? 10 : Math.min(size, 20);

        List<ManagedCommunityPost> filtered = managedCommunityPostRepository.search(typeFilter, stockCodeFilter, sentimentFilter)
                .stream()
                .sorted("HOT".equals(safeSort) ? this::compareHot : this::compareLatest)
                .toList();

        int startIndex = resolvePostStartIndex(filtered, cursor);
        int endIndex = Math.min(startIndex + safeSize, filtered.size());

        List<CommunityPostSummaryDTO> items = filtered.subList(startIndex, endIndex).stream()
                .map(post -> toSummary(post, viewer))
                .toList();

        String nextCursor = endIndex < filtered.size() ? postCursor(filtered.get(endIndex - 1)) : null;
        return CommunityPostsResponseDTO.builder()
                .items(items)
                .nextCursor(nextCursor)
                .hasNext(endIndex < filtered.size())
                .build();
    }

    @Transactional(readOnly = true)
    public CommunityPostDetailDTO getPost(User viewer, String postId) {
        return toDetail(getRequiredPost(postId), viewer);
    }

    @Transactional
    public CommunityPostMutationResponseDTO createPost(User user, CommunityPostCreateRequestDTO request) {
        validateCreateRequest(request);
        ManagedCommunityPost post = ManagedCommunityPost.builder()
                .type(normalizeType(request.getType(), true))
                .authorName(user.getNickname())
                .authorUserId(user.getId())
                .authorProfileImageUrl(resolveCharacterProfileImageUrl(user))
                .title(request.getTitle().trim())
                .content(request.getContent().trim())
                .analysisReportId(blankToNull(request.getAnalysisReportId()))
                .stockCode(normalizeStockCode(request.getStockCode()))
                .stockName(blankToNull(request.getStockName()))
                .sentiment(normalizeSentiment(request.getSentiment(), false))
                .likeCount(0)
                .build();
        ManagedCommunityPost saved = managedCommunityPostRepository.save(post);
        return CommunityPostMutationResponseDTO.builder()
                .postId(postCursor(saved))
                .createdAt(saved.getCreatedAt().toString())
                .build();
    }

    @Transactional
    public CommunityPostMutationResponseDTO updatePost(User user, String postId, CommunityPostUpdateRequestDTO request) {
        ManagedCommunityPost post = getRequiredPost(postId);
        validatePostOwner(user, post);
        if ((request.getTitle() == null || request.getTitle().isBlank())
                && (request.getContent() == null || request.getContent().isBlank())
                && request.getStockCode() == null
                && request.getStockName() == null
                && request.getSentiment() == null) {
            throw new ApiException("title, content, stockCode, stockName or sentiment is required", HttpStatus.BAD_REQUEST);
        }
        if (request.getTitle() != null) {
            post.setTitle(request.getTitle().trim());
        }
        if (request.getContent() != null) {
            post.setContent(request.getContent().trim());
        }
        if (request.getStockCode() != null) {
            post.setStockCode(normalizeStockCode(request.getStockCode()));
        }
        if (request.getStockName() != null) {
            post.setStockName(blankToNull(request.getStockName()));
        }
        if (request.getSentiment() != null) {
            post.setSentiment(normalizeSentiment(request.getSentiment(), false));
        }
        ManagedCommunityPost saved = managedCommunityPostRepository.save(post);
        return CommunityPostMutationResponseDTO.builder()
                .postId(postCursor(saved))
                .updatedAt(saved.getUpdatedAt().toString())
                .build();
    }

    @Transactional
    public void deletePost(User user, String postId) {
        ManagedCommunityPost post = getRequiredPost(postId);
        validatePostOwner(user, post);
        managedCommunityPostRepository.delete(post);
    }

    @Transactional
    public CommunityLikeResponseDTO likePost(User user, String postId) {
        ManagedCommunityPost post = getRequiredPost(postId);
        if (managedCommunityPostLikeRepository.existsByPost_IdAndUserId(post.getId(), user.getId())) {
            throw new ApiException("post already liked", HttpStatus.CONFLICT);
        }
        managedCommunityPostLikeRepository.save(ManagedCommunityPostLike.builder()
                .post(post)
                .userId(user.getId())
                .build());
        post.setLikeCount((int) managedCommunityPostLikeRepository.countByPost_Id(post.getId()));
        managedCommunityPostRepository.save(post);
        return buildLikeResponse(post, true);
    }

    @Transactional
    public CommunityLikeResponseDTO unlikePost(User user, String postId) {
        ManagedCommunityPost post = getRequiredPost(postId);
        if (!managedCommunityPostLikeRepository.existsByPost_IdAndUserId(post.getId(), user.getId())) {
            throw new ApiException("post is not liked", HttpStatus.CONFLICT);
        }
        managedCommunityPostLikeRepository.deleteByPost_IdAndUserId(post.getId(), user.getId());
        post.setLikeCount((int) managedCommunityPostLikeRepository.countByPost_Id(post.getId()));
        managedCommunityPostRepository.save(post);
        return buildLikeResponse(post, false);
    }

    @Transactional(readOnly = true)
    public CommunityCommentsResponseDTO getComments(User viewer, String postId, String cursor, Integer size) {
        ManagedCommunityPost post = getRequiredPost(postId);
        int safeSize = size == null || size < 1 ? 20 : Math.min(size, 50);
        List<ManagedCommunityComment> comments = managedCommunityCommentRepository.findByPost_IdOrderByCreatedAtAsc(post.getId());

        int startIndex = resolveCommentStartIndex(comments, cursor);
        int endIndex = Math.min(startIndex + safeSize, comments.size());
        List<CommunityCommentItemDTO> items = comments.subList(startIndex, endIndex).stream()
                .map(comment -> toCommentItem(comment, viewer))
                .toList();

        String nextCursor = endIndex < comments.size() ? commentCursor(comments.get(endIndex - 1)) : null;
        return CommunityCommentsResponseDTO.builder()
                .items(items)
                .nextCursor(nextCursor)
                .hasNext(endIndex < comments.size())
                .build();
    }

    @Transactional
    public CommunityCommentMutationResponseDTO createComment(User user, String postId, CommunityCommentCreateRequestDTO request) {
        ManagedCommunityPost post = getRequiredPost(postId);
        if (request == null || request.getContent() == null || request.getContent().isBlank()) {
            throw new ApiException("comment content is required", HttpStatus.BAD_REQUEST);
        }
        ManagedCommunityComment saved = managedCommunityCommentRepository.save(ManagedCommunityComment.builder()
                .post(post)
                .authorUserId(user.getId())
                .authorName(user.getNickname())
                .authorProfileImageUrl(resolveCharacterProfileImageUrl(user))
                .content(request.getContent().trim())
                .build());
        return CommunityCommentMutationResponseDTO.builder()
                .commentId(commentCursor(saved))
                .createdAt(saved.getCreatedAt().toString())
                .build();
    }

    @Transactional
    public void deleteComment(User user, String commentId) {
        ManagedCommunityComment comment = getRequiredComment(commentId);
        validateCommentOwner(user, comment);
        managedCommunityCommentRepository.delete(comment);
    }

    @Transactional
    public CommunityReportResponseDTO reportPost(User user, String postId, CommunityReportRequestDTO request) {
        ManagedCommunityPost post = getRequiredPost(postId);
        String reason = normalizeReportReason(request);
        if (managedCommunityReportRepository.existsByTargetTypeAndPostIdAndReporterUserId("POST", post.getId(), user.getId())) {
            throw new ApiException("post already reported", HttpStatus.CONFLICT);
        }
        ManagedCommunityReport report = managedCommunityReportRepository.save(ManagedCommunityReport.builder()
                .targetType("POST")
                .postId(post.getId())
                .reporterUserId(user.getId())
                .reason(reason)
                .build());
        return CommunityReportResponseDTO.builder()
                .reported(true)
                .createdAt(report.getCreatedAt().toString())
                .build();
    }

    @Transactional
    public CommunityReportResponseDTO reportComment(User user, String commentId, CommunityReportRequestDTO request) {
        ManagedCommunityComment comment = getRequiredComment(commentId);
        String reason = normalizeReportReason(request);
        if (managedCommunityReportRepository.existsByTargetTypeAndCommentIdAndReporterUserId("COMMENT", comment.getId(), user.getId())) {
            throw new ApiException("comment already reported", HttpStatus.CONFLICT);
        }
        ManagedCommunityReport report = managedCommunityReportRepository.save(ManagedCommunityReport.builder()
                .targetType("COMMENT")
                .commentId(comment.getId())
                .reporterUserId(user.getId())
                .reason(reason)
                .build());
        return CommunityReportResponseDTO.builder()
                .reported(true)
                .createdAt(report.getCreatedAt().toString())
                .build();
    }

    @Transactional(readOnly = true)
    public InvestorSentimentDTO getInvestorSentiment(String stockCode) {
        String normalized = normalizeStockCode(stockCode);
        if (normalized == null) {
            return emptySentiment();
        }
        List<ManagedCommunityPost> posts = managedCommunityPostRepository.findByStockCodeOrderByCreatedAtDescIdDesc(normalized);
        int bullish = countSentiment(posts, "BULLISH");
        int bearish = countSentiment(posts, "BEARISH");
        int neutral = countSentiment(posts, "NEUTRAL");
        int total = bullish + bearish + neutral;
        if (total == 0) {
            return emptySentiment();
        }
        return InvestorSentimentDTO.builder()
                .bullishCount(bullish)
                .bearishCount(bearish)
                .neutralCount(neutral)
                .bullishPercentage((int) Math.round(bullish * 100.0 / total))
                .bearishPercentage((int) Math.round(bearish * 100.0 / total))
                .neutralPercentage(Math.max(0, 100 - (int) Math.round(bullish * 100.0 / total) - (int) Math.round(bearish * 100.0 / total)))
                .build();
    }

    @Transactional(readOnly = true)
    public int getDiscussionCount(String stockCode) {
        String normalized = normalizeStockCode(stockCode);
        if (normalized == null) {
            return 0;
        }
        return managedCommunityPostRepository.findByStockCodeOrderByCreatedAtDescIdDesc(normalized).size();
    }

    private int countSentiment(List<ManagedCommunityPost> posts, String sentiment) {
        return (int) posts.stream()
                .filter(post -> sentiment.equalsIgnoreCase(post.getSentiment() == null ? "" : post.getSentiment()))
                .count();
    }

    private InvestorSentimentDTO emptySentiment() {
        return InvestorSentimentDTO.builder()
                .bullishCount(0)
                .bearishCount(0)
                .neutralCount(0)
                .bullishPercentage(0)
                .bearishPercentage(0)
                .neutralPercentage(0)
                .build();
    }

    private int compareLatest(ManagedCommunityPost left, ManagedCommunityPost right) {
        int createdAt = right.getCreatedAt().compareTo(left.getCreatedAt());
        if (createdAt != 0) {
            return createdAt;
        }
        return Long.compare(right.getId(), left.getId());
    }

    private int compareHot(ManagedCommunityPost left, ManagedCommunityPost right) {
        int leftScore = safeLikeCount(left) + left.getComments().size() * 2;
        int rightScore = safeLikeCount(right) + right.getComments().size() * 2;
        int byScore = Integer.compare(rightScore, leftScore);
        if (byScore != 0) {
            return byScore;
        }
        return compareLatest(left, right);
    }

    private int safeLikeCount(ManagedCommunityPost post) {
        return post.getLikeCount() != null ? post.getLikeCount() : 0;
    }

    private int resolvePostStartIndex(List<ManagedCommunityPost> posts, String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return 0;
        }
        for (int i = 0; i < posts.size(); i++) {
            if (postCursor(posts.get(i)).equals(cursor)) {
                return i + 1;
            }
        }
        throw new ApiException("invalid cursor", HttpStatus.BAD_REQUEST);
    }

    private int resolveCommentStartIndex(List<ManagedCommunityComment> comments, String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return 0;
        }
        for (int i = 0; i < comments.size(); i++) {
            if (commentCursor(comments.get(i)).equals(cursor)) {
                return i + 1;
            }
        }
        throw new ApiException("invalid cursor", HttpStatus.BAD_REQUEST);
    }

    private String postCursor(ManagedCommunityPost post) {
        return "POST_" + post.getId();
    }

    private String commentCursor(ManagedCommunityComment comment) {
        return "COMMENT_" + comment.getId();
    }

    private ManagedCommunityPost getRequiredPost(String postId) {
        return managedCommunityPostRepository.findById(parseCursorId(postId, "POST_"))
                .orElseThrow(() -> new ApiException("post not found", HttpStatus.NOT_FOUND));
    }

    private ManagedCommunityComment getRequiredComment(String commentId) {
        return managedCommunityCommentRepository.findById(parseCursorId(commentId, "COMMENT_"))
                .orElseThrow(() -> new ApiException("comment not found", HttpStatus.NOT_FOUND));
    }

    private long parseCursorId(String value, String prefix) {
        if (value == null || value.isBlank()) {
            throw new ApiException("id is required", HttpStatus.BAD_REQUEST);
        }
        String normalized = value.startsWith(prefix) ? value.substring(prefix.length()) : value;
        try {
            return Long.parseLong(normalized);
        } catch (NumberFormatException ex) {
            throw new ApiException("invalid id", HttpStatus.BAD_REQUEST);
        }
    }

    private void validateCreateRequest(CommunityPostCreateRequestDTO request) {
        if (request == null) {
            throw new ApiException("request is required", HttpStatus.BAD_REQUEST);
        }
        normalizeType(request.getType(), true);
        if (request.getTitle() == null || request.getTitle().isBlank()) {
            throw new ApiException("title is required", HttpStatus.BAD_REQUEST);
        }
        if (request.getContent() == null || request.getContent().isBlank()) {
            throw new ApiException("content is required", HttpStatus.BAD_REQUEST);
        }
        normalizeSentiment(request.getSentiment(), false);
    }

    private String normalizeType(String type, boolean required) {
        if (type == null || type.isBlank()) {
            if (required) {
                throw new ApiException("type is required", HttpStatus.BAD_REQUEST);
            }
            return null;
        }
        String normalized = type.trim().toUpperCase(Locale.ROOT);
        if (!POST_TYPES.contains(normalized)) {
            throw new ApiException("invalid post type", HttpStatus.BAD_REQUEST);
        }
        return normalized;
    }

    private String normalizeSort(String sort) {
        if (sort == null || sort.isBlank()) {
            return "LATEST";
        }
        String normalized = sort.trim().toUpperCase(Locale.ROOT);
        if (!FEED_SORTS.contains(normalized)) {
            throw new ApiException("invalid sort", HttpStatus.BAD_REQUEST);
        }
        return normalized;
    }

    private String normalizeSentiment(String sentiment, boolean required) {
        if (sentiment == null || sentiment.isBlank()) {
            if (required) {
                throw new ApiException("sentiment is required", HttpStatus.BAD_REQUEST);
            }
            return null;
        }
        String normalized = sentiment.trim().toUpperCase(Locale.ROOT);
        if (!SENTIMENTS.contains(normalized)) {
            throw new ApiException("invalid sentiment", HttpStatus.BAD_REQUEST);
        }
        return normalized;
    }

    private String normalizeReportReason(CommunityReportRequestDTO request) {
        if (request == null || request.getReason() == null || request.getReason().isBlank()) {
            throw new ApiException("reason is required", HttpStatus.BAD_REQUEST);
        }
        String normalized = request.getReason().trim().toUpperCase(Locale.ROOT);
        if (!REPORT_REASONS.contains(normalized)) {
            throw new ApiException("invalid report reason", HttpStatus.BAD_REQUEST);
        }
        return normalized;
    }

    private String normalizeStockCode(String stockCode) {
        if (stockCode == null || stockCode.isBlank()) {
            return null;
        }
        String trimmed = stockCode.trim();
        return trimmed.length() >= 6 ? trimmed : String.format("%06d", Integer.parseInt(trimmed));
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private void validatePostOwner(User user, ManagedCommunityPost post) {
        if (user == null || user.getId() == null || !user.getId().equals(post.getAuthorUserId())) {
            throw new ApiException("forbidden", HttpStatus.FORBIDDEN);
        }
    }

    private void validateCommentOwner(User user, ManagedCommunityComment comment) {
        if (user == null || user.getId() == null || !user.getId().equals(comment.getAuthorUserId())) {
            throw new ApiException("forbidden", HttpStatus.FORBIDDEN);
        }
    }

    private CommunityPostSummaryDTO toSummary(ManagedCommunityPost post, User viewer) {
        String market = marketFor(post.getStockCode());
        String logoUrl = null;
        return CommunityPostSummaryDTO.builder()
                .postId(postCursor(post))
                .type(post.getType())
                .author(toAuthor(post.getAuthorName(), post.getAuthorProfileImageUrl()))
                .title(post.getTitle())
                .content(post.getContent())
                .stockCode(post.getStockCode())
                .stockName(post.getStockName())
                .market(market)
                .logoUrl(logoUrl)
                .visual(stockVisual(post.getStockCode(), post.getStockName(), market, logoUrl))
                .sentiment(post.getSentiment())
                .likeCount(safeLikeCount(post))
                .commentCount((int) managedCommunityCommentRepository.countByPost_Id(post.getId()))
                .liked(isLiked(viewer, post))
                .isMine(isMine(viewer, post.getAuthorUserId()))
                .createdAt(post.getCreatedAt().toString())
                .build();
    }

    private CommunityPostDetailDTO toDetail(ManagedCommunityPost post, User viewer) {
        String market = marketFor(post.getStockCode());
        String logoUrl = null;
        return CommunityPostDetailDTO.builder()
                .postId(postCursor(post))
                .type(post.getType())
                .author(toAuthor(post.getAuthorName(), post.getAuthorProfileImageUrl()))
                .title(post.getTitle())
                .content(post.getContent())
                .stockCode(post.getStockCode())
                .stockName(post.getStockName())
                .market(market)
                .logoUrl(logoUrl)
                .visual(stockVisual(post.getStockCode(), post.getStockName(), market, logoUrl))
                .sentiment(post.getSentiment())
                .likeCount(safeLikeCount(post))
                .commentCount((int) managedCommunityCommentRepository.countByPost_Id(post.getId()))
                .liked(isLiked(viewer, post))
                .isMine(isMine(viewer, post.getAuthorUserId()))
                .createdAt(post.getCreatedAt().toString())
                .updatedAt(post.getUpdatedAt().toString())
                .build();
    }

    private CommunityCommentItemDTO toCommentItem(ManagedCommunityComment comment, User viewer) {
        return CommunityCommentItemDTO.builder()
                .commentId(commentCursor(comment))
                .author(toAuthor(comment.getAuthorName(), comment.getAuthorProfileImageUrl()))
                .content(comment.getContent())
                .isMine(isMine(viewer, comment.getAuthorUserId()))
                .createdAt(comment.getCreatedAt().toString())
                .build();
    }

    private CommunityAuthorDTO toAuthor(String nickname, String profileImageUrl) {
        return CommunityAuthorDTO.builder()
                .nickname(nickname)
                .profileImageUrl(profileImageUrl)
                .build();
    }

    private String resolveCharacterProfileImageUrl(User user) {
        if (user == null || user.getId() == null) {
            return "";
        }
        return profileImageUrlService.resolveCharacterProfileImageUrl(
                user,
                userMyPagePreferenceRepository.findById(user.getId()).orElse(null)
        );
    }

    private boolean isMine(User viewer, Long authorUserId) {
        return viewer != null && viewer.getId() != null && viewer.getId().equals(authorUserId);
    }

    private boolean isLiked(User viewer, ManagedCommunityPost post) {
        return viewer != null
                && viewer.getId() != null
                && managedCommunityPostLikeRepository.existsByPost_IdAndUserId(post.getId(), viewer.getId());
    }

    private String marketFor(String stockCode) {
        return stockCode == null || stockCode.isBlank() ? null : "KRX";
    }

    private StockVisualDTO stockVisual(String stockCode, String stockName, String market, String logoUrl) {
        if (stockCode == null || stockCode.isBlank()) {
            return null;
        }
        return stockVisualAssetResolver.resolve(market, stockCode, stockName, logoUrl);
    }

    private CommunityLikeResponseDTO buildLikeResponse(ManagedCommunityPost post, boolean liked) {
        return CommunityLikeResponseDTO.builder()
                .postId(postCursor(post))
                .liked(liked)
                .likeCount(safeLikeCount(post))
                .build();
    }
}

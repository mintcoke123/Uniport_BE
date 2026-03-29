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
import com.uniport.entity.User;
import com.uniport.exception.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Service
public class CommunityMockService {

    private static final Set<String> POST_TYPES = Set.of("GENERAL", "ACHIEVEMENT", "ANALYSIS_SHARE");
    private static final Set<String> FEED_SORTS = Set.of("LATEST", "HOT");
    private static final Set<String> REPORT_REASONS = Set.of("SPAM", "ABUSE", "HATE", "ADULT", "OTHER");

    private final LinkedHashMap<String, CommunityPostState> posts = new LinkedHashMap<>();
    private final ConcurrentHashMap<String, Set<Long>> postLikes = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Set<Long>> postReports = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Set<Long>> commentReports = new ConcurrentHashMap<>();
    private final AtomicInteger postSequence = new AtomicInteger(201);
    private final AtomicInteger commentSequence = new AtomicInteger(101);

    public CommunityMockService() {
        seedPosts();
    }

    public CommunityPostsResponseDTO getPosts(User viewer, String sort, String type, String cursor, Integer size) {
        String safeSort = normalizeSort(sort);
        int safeSize = size == null || size < 1 ? 10 : Math.min(size, 20);
        if (type != null && !type.isBlank() && !POST_TYPES.contains(type.toUpperCase(Locale.ROOT))) {
            throw new ApiException("invalid post type", HttpStatus.BAD_REQUEST);
        }

        List<CommunityPostState> filtered = posts.values().stream()
                .filter(post -> post.type.equalsIgnoreCase(type == null ? post.type : type))
                .sorted(resolvePostComparator(safeSort))
                .toList();

        int startIndex = resolveStartIndex(filtered, cursor);
        int endIndex = Math.min(startIndex + safeSize, filtered.size());

        List<CommunityPostSummaryDTO> items = filtered.subList(startIndex, endIndex).stream()
                .map(post -> toSummary(post, viewer))
                .toList();

        String nextCursor = endIndex < filtered.size() ? filtered.get(endIndex - 1).postId : null;
        return CommunityPostsResponseDTO.builder()
                .items(items)
                .nextCursor(nextCursor)
                .hasNext(endIndex < filtered.size())
                .build();
    }

    public CommunityPostDetailDTO getPost(User viewer, String postId) {
        return toDetail(getRequiredPost(postId), viewer);
    }

    public CommunityPostMutationResponseDTO createPost(User user, CommunityPostCreateRequestDTO request) {
        validateCreateRequest(request);
        String postId = "POST_" + postSequence.getAndIncrement();
        Instant now = Instant.now();

        CommunityPostState state = new CommunityPostState(
                postId,
                request.getType().toUpperCase(Locale.ROOT),
                user.getId(),
                user.getNickname(),
                user.getProfileImageUrl(),
                request.getTitle(),
                request.getContent(),
                request.getAnalysisReportId(),
                now,
                null,
                new ArrayList<>()
        );
        posts.put(postId, state);
        postLikes.put(postId, ConcurrentHashMap.newKeySet());

        return CommunityPostMutationResponseDTO.builder()
                .postId(postId)
                .createdAt(now.toString())
                .build();
    }

    public CommunityPostMutationResponseDTO updatePost(User user, String postId, CommunityPostUpdateRequestDTO request) {
        CommunityPostState post = getRequiredPost(postId);
        validatePostOwner(user, post);
        if ((request.getTitle() == null || request.getTitle().isBlank()) &&
                (request.getContent() == null || request.getContent().isBlank())) {
            throw new ApiException("title or content is required", HttpStatus.BAD_REQUEST);
        }

        if (request.getTitle() != null) {
            post.title = request.getTitle();
        }
        if (request.getContent() != null) {
            post.content = request.getContent();
        }
        post.updatedAt = Instant.now();

        return CommunityPostMutationResponseDTO.builder()
                .postId(postId)
                .updatedAt(post.updatedAt.toString())
                .build();
    }

    public void deletePost(User user, String postId) {
        CommunityPostState post = getRequiredPost(postId);
        validatePostOwner(user, post);
        posts.remove(postId);
        postLikes.remove(postId);
        postReports.remove(postId);
    }

    public CommunityLikeResponseDTO likePost(User user, String postId) {
        CommunityPostState post = getRequiredPost(postId);
        Set<Long> likedUsers = postLikes.computeIfAbsent(postId, ignored -> ConcurrentHashMap.newKeySet());
        if (!likedUsers.add(user.getId())) {
            throw new ApiException("post already liked", HttpStatus.CONFLICT);
        }
        return buildLikeResponse(post, likedUsers.contains(user.getId()), likedUsers.size());
    }

    public CommunityLikeResponseDTO unlikePost(User user, String postId) {
        CommunityPostState post = getRequiredPost(postId);
        Set<Long> likedUsers = postLikes.computeIfAbsent(postId, ignored -> ConcurrentHashMap.newKeySet());
        if (!likedUsers.remove(user.getId())) {
            throw new ApiException("post is not liked", HttpStatus.CONFLICT);
        }
        return buildLikeResponse(post, false, likedUsers.size());
    }

    public CommunityCommentsResponseDTO getComments(User viewer, String postId, String cursor, Integer size) {
        CommunityPostState post = getRequiredPost(postId);
        int safeSize = size == null || size < 1 ? 20 : Math.min(size, 50);
        List<CommentState> comments = post.comments.stream()
                .sorted(Comparator.comparing(CommentState::createdAt))
                .toList();

        int startIndex = resolveCommentStartIndex(comments, cursor);
        int endIndex = Math.min(startIndex + safeSize, comments.size());

        List<CommunityCommentItemDTO> items = comments.subList(startIndex, endIndex).stream()
                .map(comment -> toCommentItem(comment, viewer))
                .toList();

        String nextCursor = endIndex < comments.size() ? comments.get(endIndex - 1).commentId : null;
        return CommunityCommentsResponseDTO.builder()
                .items(items)
                .nextCursor(nextCursor)
                .hasNext(endIndex < comments.size())
                .build();
    }

    public CommunityCommentMutationResponseDTO createComment(User user, String postId, CommunityCommentCreateRequestDTO request) {
        CommunityPostState post = getRequiredPost(postId);
        if (request == null || request.getContent() == null || request.getContent().isBlank()) {
            throw new ApiException("comment content is required", HttpStatus.BAD_REQUEST);
        }

        Instant now = Instant.now();
        String commentId = "COMMENT_" + commentSequence.getAndIncrement();
        post.comments.add(new CommentState(
                commentId,
                user.getId(),
                user.getNickname(),
                user.getProfileImageUrl(),
                request.getContent(),
                now
        ));

        return CommunityCommentMutationResponseDTO.builder()
                .commentId(commentId)
                .createdAt(now.toString())
                .build();
    }

    public void deleteComment(User user, String commentId) {
        for (CommunityPostState post : posts.values()) {
            CommentState found = post.comments.stream()
                    .filter(comment -> comment.commentId.equals(commentId))
                    .findFirst()
                    .orElse(null);
            if (found == null) {
                continue;
            }
            validateCommentOwner(user, found);
            post.comments.remove(found);
            commentReports.remove(commentId);
            return;
        }
        throw new ApiException("comment not found", HttpStatus.NOT_FOUND);
    }

    public CommunityReportResponseDTO reportPost(User user, String postId, CommunityReportRequestDTO request) {
        getRequiredPost(postId);
        validateReportRequest(request);
        Set<Long> reporters = postReports.computeIfAbsent(postId, ignored -> ConcurrentHashMap.newKeySet());
        if (!reporters.add(user.getId())) {
            throw new ApiException("post already reported", HttpStatus.CONFLICT);
        }
        return CommunityReportResponseDTO.builder()
                .reported(Boolean.TRUE)
                .createdAt(Instant.now().toString())
                .build();
    }

    public CommunityReportResponseDTO reportComment(User user, String commentId, CommunityReportRequestDTO request) {
        findComment(commentId);
        validateReportRequest(request);
        Set<Long> reporters = commentReports.computeIfAbsent(commentId, ignored -> ConcurrentHashMap.newKeySet());
        if (!reporters.add(user.getId())) {
            throw new ApiException("comment already reported", HttpStatus.CONFLICT);
        }
        return CommunityReportResponseDTO.builder()
                .reported(Boolean.TRUE)
                .createdAt(Instant.now().toString())
                .build();
    }

    private void seedPosts() {
        CommunityPostState first = new CommunityPostState(
                "POST_101", "ACHIEVEMENT", 1L, "투자곰", "https://example.com/profile-bear.png",
                "400일 연속 학습 달성!", "오늘도 투자 공부 완료했습니다.", null,
                Instant.parse("2026-03-11T08:20:00Z"), null, new ArrayList<>()
        );
        first.comments.add(new CommentState("COMMENT_101", 2L, "이민지", "https://example.com/profile-minji.png", "축하해요!", Instant.parse("2026-03-11T08:25:00Z")));
        first.comments.add(new CommentState("COMMENT_102", 3L, "박서준", "https://example.com/profile-seojun.png", "대단하네요.", Instant.parse("2026-03-11T08:27:00Z")));

        CommunityPostState second = new CommunityPostState(
                "POST_102", "ANALYSIS_SHARE", 2L, "이민지", "https://example.com/profile-minji.png",
                "Growth Strategy", "내 ETF 분석 결과 공유합니다.", "REPORT_301",
                Instant.parse("2026-03-11T07:40:00Z"), null, new ArrayList<>()
        );

        CommunityPostState third = new CommunityPostState(
                "POST_103", "GENERAL", 4L, "주식토끼", "https://example.com/profile-rabbit.png",
                null, "오늘 장 초반 변동성이 꽤 크네요.", null,
                Instant.parse("2026-03-10T18:00:00Z"), null, new ArrayList<>()
        );

        posts.put(first.postId, first);
        posts.put(second.postId, second);
        posts.put(third.postId, third);

        Set<Long> likes101 = ConcurrentHashMap.newKeySet();
        likes101.add(2L);
        likes101.add(3L);
        likes101.add(4L);
        postLikes.put("POST_101", likes101);

        Set<Long> likes102 = ConcurrentHashMap.newKeySet();
        likes102.add(1L);
        postLikes.put("POST_102", likes102);

        postLikes.put("POST_103", ConcurrentHashMap.newKeySet());
    }

    private CommunityPostState getRequiredPost(String postId) {
        CommunityPostState post = posts.get(postId);
        if (post == null) {
            throw new ApiException("post not found", HttpStatus.NOT_FOUND);
        }
        return post;
    }

    private CommentState findComment(String commentId) {
        return posts.values().stream()
                .flatMap(post -> post.comments.stream())
                .filter(comment -> comment.commentId.equals(commentId))
                .findFirst()
                .orElseThrow(() -> new ApiException("comment not found", HttpStatus.NOT_FOUND));
    }

    private String normalizeSort(String sort) {
        if (sort == null || sort.isBlank()) {
            return "LATEST";
        }
        String normalized = sort.toUpperCase(Locale.ROOT);
        if (!FEED_SORTS.contains(normalized)) {
            throw new ApiException("invalid sort", HttpStatus.BAD_REQUEST);
        }
        return normalized;
    }

    private Comparator<CommunityPostState> resolvePostComparator(String sort) {
        if ("HOT".equals(sort)) {
            return Comparator.comparingInt(this::scoreHot).reversed()
                    .thenComparing(CommunityPostState::createdAt).reversed();
        }
        return Comparator.comparing(CommunityPostState::createdAt).reversed();
    }

    private int scoreHot(CommunityPostState post) {
        return likeCount(post.postId) * 2 + post.comments.size();
    }

    private int resolveStartIndex(List<CommunityPostState> posts, String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return 0;
        }
        for (int index = 0; index < posts.size(); index++) {
            if (posts.get(index).postId.equals(cursor)) {
                return index + 1;
            }
        }
        throw new ApiException("invalid cursor", HttpStatus.BAD_REQUEST);
    }

    private int resolveCommentStartIndex(List<CommentState> comments, String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return 0;
        }
        for (int index = 0; index < comments.size(); index++) {
            if (comments.get(index).commentId.equals(cursor)) {
                return index + 1;
            }
        }
        throw new ApiException("invalid cursor", HttpStatus.BAD_REQUEST);
    }

    private CommunityPostSummaryDTO toSummary(CommunityPostState post, User viewer) {
        return CommunityPostSummaryDTO.builder()
                .postId(post.postId)
                .type(post.type)
                .author(author(post.authorNickname, post.authorProfileImageUrl))
                .title(post.title)
                .content(post.content)
                .likeCount(likeCount(post.postId))
                .commentCount(post.comments.size())
                .liked(isLiked(viewer, post.postId))
                .isMine(isMine(viewer, post.authorUserId))
                .createdAt(post.createdAt.toString())
                .build();
    }

    private CommunityPostDetailDTO toDetail(CommunityPostState post, User viewer) {
        return CommunityPostDetailDTO.builder()
                .postId(post.postId)
                .type(post.type)
                .author(author(post.authorNickname, post.authorProfileImageUrl))
                .title(post.title)
                .content(post.content)
                .likeCount(likeCount(post.postId))
                .commentCount(post.comments.size())
                .liked(isLiked(viewer, post.postId))
                .isMine(isMine(viewer, post.authorUserId))
                .createdAt(post.createdAt.toString())
                .updatedAt(post.updatedAt == null ? null : post.updatedAt.toString())
                .build();
    }

    private CommunityCommentItemDTO toCommentItem(CommentState comment, User viewer) {
        return CommunityCommentItemDTO.builder()
                .commentId(comment.commentId)
                .author(author(comment.authorNickname, comment.authorProfileImageUrl))
                .content(comment.content)
                .isMine(isMine(viewer, comment.authorUserId))
                .createdAt(comment.createdAt.toString())
                .build();
    }

    private CommunityAuthorDTO author(String nickname, String profileImageUrl) {
        return CommunityAuthorDTO.builder()
                .nickname(nickname)
                .profileImageUrl(profileImageUrl)
                .build();
    }

    private int likeCount(String postId) {
        return postLikes.getOrDefault(postId, Set.of()).size();
    }

    private boolean isLiked(User viewer, String postId) {
        return viewer != null && postLikes.getOrDefault(postId, Set.of()).contains(viewer.getId());
    }

    private boolean isMine(User viewer, Long authorUserId) {
        return viewer != null && viewer.getId() != null && viewer.getId().equals(authorUserId);
    }

    private void validateCreateRequest(CommunityPostCreateRequestDTO request) {
        if (request == null || request.getType() == null || !POST_TYPES.contains(request.getType().toUpperCase(Locale.ROOT))) {
            throw new ApiException("invalid post type", HttpStatus.BAD_REQUEST);
        }

        String type = request.getType().toUpperCase(Locale.ROOT);
        if ("GENERAL".equals(type) && isBlank(request.getContent())) {
            throw new ApiException("content is required for GENERAL", HttpStatus.UNPROCESSABLE_ENTITY);
        }
        if ("ACHIEVEMENT".equals(type) && isBlank(request.getTitle()) && isBlank(request.getContent())) {
            throw new ApiException("title or content is required for ACHIEVEMENT", HttpStatus.UNPROCESSABLE_ENTITY);
        }
        if ("ANALYSIS_SHARE".equals(type) && isBlank(request.getAnalysisReportId())) {
            throw new ApiException("analysisReportId is required for ANALYSIS_SHARE", HttpStatus.UNPROCESSABLE_ENTITY);
        }
    }

    private void validateReportRequest(CommunityReportRequestDTO request) {
        if (request == null || request.getReason() == null || !REPORT_REASONS.contains(request.getReason().toUpperCase(Locale.ROOT))) {
            throw new ApiException("invalid report reason", HttpStatus.BAD_REQUEST);
        }
    }

    private void validatePostOwner(User user, CommunityPostState post) {
        if (user == null) {
            throw new ApiException("Authentication is required", HttpStatus.UNAUTHORIZED);
        }
        if (isAdmin(user)) {
            return;
        }
        if (!user.getId().equals(post.authorUserId)) {
            throw new ApiException("no permission for this post", HttpStatus.FORBIDDEN);
        }
    }

    private void validateCommentOwner(User user, CommentState comment) {
        if (user == null) {
            throw new ApiException("Authentication is required", HttpStatus.UNAUTHORIZED);
        }
        if (isAdmin(user)) {
            return;
        }
        if (!user.getId().equals(comment.authorUserId)) {
            throw new ApiException("no permission for this comment", HttpStatus.FORBIDDEN);
        }
    }

    private boolean isAdmin(User user) {
        return user.getRole() != null && "admin".equalsIgnoreCase(user.getRole());
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private CommunityLikeResponseDTO buildLikeResponse(CommunityPostState post, boolean liked, int likeCount) {
        return CommunityLikeResponseDTO.builder()
                .postId(post.postId)
                .liked(liked)
                .likeCount(likeCount)
                .build();
    }

    private static final class CommunityPostState {
        private final String postId;
        private final String type;
        private final Long authorUserId;
        private final String authorNickname;
        private final String authorProfileImageUrl;
        private String title;
        private String content;
        private final String analysisReportId;
        private final Instant createdAt;
        private Instant updatedAt;
        private final ArrayList<CommentState> comments;

        private CommunityPostState(String postId,
                                   String type,
                                   Long authorUserId,
                                   String authorNickname,
                                   String authorProfileImageUrl,
                                   String title,
                                   String content,
                                   String analysisReportId,
                                   Instant createdAt,
                                   Instant updatedAt,
                                   ArrayList<CommentState> comments) {
            this.postId = postId;
            this.type = type;
            this.authorUserId = authorUserId;
            this.authorNickname = authorNickname;
            this.authorProfileImageUrl = authorProfileImageUrl;
            this.title = title;
            this.content = content;
            this.analysisReportId = analysisReportId;
            this.createdAt = createdAt;
            this.updatedAt = updatedAt;
            this.comments = comments;
        }

        private Instant createdAt() {
            return createdAt;
        }
    }

    private record CommentState(
            String commentId,
            Long authorUserId,
            String authorNickname,
            String authorProfileImageUrl,
            String content,
            Instant createdAt
    ) {
    }
}

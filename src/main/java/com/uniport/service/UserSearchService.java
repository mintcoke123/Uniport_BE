package com.uniport.service;

import com.uniport.dto.UserSearchItemDTO;
import com.uniport.entity.FriendRelation;
import com.uniport.entity.LearningUserStateEntity;
import com.uniport.entity.User;
import com.uniport.repository.FriendRelationRepository;
import com.uniport.repository.LearningUserStateRepository;
import com.uniport.repository.UserMyPagePreferenceRepository;
import com.uniport.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class UserSearchService {

    private final UserRepository userRepository;
    private final UserMyPagePreferenceRepository userMyPagePreferenceRepository;
    private final FriendRelationRepository friendRelationRepository;
    private final LearningUserStateRepository learningUserStateRepository;
    private final ProfileImageUrlService profileImageUrlService;

    public UserSearchService(UserRepository userRepository,
                             UserMyPagePreferenceRepository userMyPagePreferenceRepository,
                             FriendRelationRepository friendRelationRepository,
                             LearningUserStateRepository learningUserStateRepository,
                             ProfileImageUrlService profileImageUrlService) {
        this.userRepository = userRepository;
        this.userMyPagePreferenceRepository = userMyPagePreferenceRepository;
        this.friendRelationRepository = friendRelationRepository;
        this.learningUserStateRepository = learningUserStateRepository;
        this.profileImageUrlService = profileImageUrlService;
    }

    public List<UserSearchItemDTO> search(User currentUser, String keyword, Integer limit) {
        String resolvedKeyword = keyword != null ? keyword.trim() : "";
        int resolvedLimit = (limit != null && limit > 0) ? Math.min(limit, 20) : 10;
        return userRepository.findTop10ByNicknameContainingIgnoreCaseOrStudentIdContaining(resolvedKeyword, resolvedKeyword).stream()
                .filter(user -> currentUser == null || !user.getId().equals(currentUser.getId()))
                .limit(resolvedLimit)
                .map(user -> toSearchItem(currentUser, user))
                .toList();
    }

    private UserSearchItemDTO toSearchItem(User currentUser, User user) {
        String relationStatus = resolveRelationStatus(currentUser, user);
        return UserSearchItemDTO.builder()
                .id(user.getId())
                .nickname(user.getNickname())
                .studentId(user.getStudentId())
                .profileImageUrl(profileImageUrlService.resolveCharacterProfileImageUrl(
                        user,
                        userMyPagePreferenceRepository.findById(user.getId()).orElse(null)
                ))
                .level(resolveLearningLevel(user))
                .investmentProfileLabel(user.getInvestmentProfileResult() != null && !user.getInvestmentProfileResult().isBlank()
                        ? user.getInvestmentProfileResult()
                        : "균형잡힌 판다형")
                .alreadyInvited("REQUESTED".equalsIgnoreCase(relationStatus))
                .alreadyMatched("ACCEPTED".equalsIgnoreCase(relationStatus))
                .build();
    }

    private String resolveRelationStatus(User currentUser, User user) {
        if (currentUser == null || currentUser.getId() == null || user.getId() == null) {
            return "NONE";
        }
        return friendRelationRepository.findBetweenUsers(currentUser.getId(), user.getId())
                .map(FriendRelation::getStatus)
                .orElse("NONE");
    }

    private int resolveLearningLevel(User user) {
        if (user.getId() == null) {
            return 1;
        }
        return learningUserStateRepository.findById(user.getId())
                .map(this::toLearningLevel)
                .orElse(1);
    }

    private int toLearningLevel(LearningUserStateEntity state) {
        int totalExp = state.getExp() != null
                ? Math.max(0, state.getExp())
                : Math.max(0, state.getPoint());
        return LearningProgressPolicy.fromExp(totalExp).level();
    }
}

package com.uniport.service;

import com.uniport.dto.UserSearchItemDTO;
import com.uniport.entity.User;
import com.uniport.repository.UserMyPagePreferenceRepository;
import com.uniport.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class UserSearchService {

    private final UserRepository userRepository;
    private final UserMyPagePreferenceRepository userMyPagePreferenceRepository;
    private final ProfileImageUrlService profileImageUrlService;

    public UserSearchService(UserRepository userRepository,
                             UserMyPagePreferenceRepository userMyPagePreferenceRepository,
                             ProfileImageUrlService profileImageUrlService) {
        this.userRepository = userRepository;
        this.userMyPagePreferenceRepository = userMyPagePreferenceRepository;
        this.profileImageUrlService = profileImageUrlService;
    }

    public List<UserSearchItemDTO> search(User currentUser, String keyword, Integer limit) {
        String resolvedKeyword = keyword != null ? keyword.trim() : "";
        int resolvedLimit = (limit != null && limit > 0) ? Math.min(limit, 20) : 10;
        return userRepository.findTop10ByNicknameContainingIgnoreCaseOrStudentIdContaining(resolvedKeyword, resolvedKeyword).stream()
                .filter(user -> currentUser == null || !user.getId().equals(currentUser.getId()))
                .limit(resolvedLimit)
                .map(user -> UserSearchItemDTO.builder()
                        .id(user.getId())
                        .nickname(user.getNickname())
                        .studentId(user.getStudentId())
                        .profileImageUrl(profileImageUrlService.resolveCharacterProfileImageUrl(
                                user,
                                userMyPagePreferenceRepository.findById(user.getId()).orElse(null)
                        ))
                        .level(15)
                        .investmentProfileLabel(user.getInvestmentProfileResult() != null && !user.getInvestmentProfileResult().isBlank()
                                ? user.getInvestmentProfileResult()
                                : "균형잡힌 판다형")
                        .alreadyInvited(false)
                        .alreadyMatched(user.getTeamId() != null && !user.getTeamId().isBlank())
                        .build())
                .toList();
    }
}

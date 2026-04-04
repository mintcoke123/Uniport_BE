package com.uniport.service;

import com.uniport.dto.UserSearchItemDTO;
import com.uniport.entity.User;
import com.uniport.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class UserSearchService {

    private final UserRepository userRepository;

    public UserSearchService(UserRepository userRepository) {
        this.userRepository = userRepository;
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
                        .profileImageUrl(user.getProfileImageUrl())
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

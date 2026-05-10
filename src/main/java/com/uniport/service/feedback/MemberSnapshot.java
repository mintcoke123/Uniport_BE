package com.uniport.service.feedback;

public record MemberSnapshot(
        Long memberId,
        String nickname,
        String avatarUrl
) {
}

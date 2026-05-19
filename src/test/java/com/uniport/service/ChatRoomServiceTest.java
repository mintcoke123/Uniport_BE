package com.uniport.service;

import com.uniport.entity.ChatMessage;
import com.uniport.entity.MatchingRoom;
import com.uniport.entity.MatchingRoomMember;
import com.uniport.entity.User;
import com.uniport.entity.UserMyPagePreference;
import com.uniport.entity.Vote;
import com.uniport.entity.VoteParticipant;
import com.uniport.exception.ApiException;
import com.uniport.repository.ChatMessageRepository;
import com.uniport.repository.MatchingRoomMemberRepository;
import com.uniport.repository.MatchingRoomRepository;
import com.uniport.repository.TeamAccountRepository;
import com.uniport.repository.TeamHoldingRepository;
import com.uniport.repository.UserMyPagePreferenceRepository;
import com.uniport.repository.VoteParticipantRepository;
import com.uniport.repository.VoteRepository;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.AbstractList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChatRoomServiceTest {

    @Test
    void getMyChatRooms_returnsStartedAndEndedRooms() {
        MatchingRoomMemberRepository matchingRoomMemberRepository = mock(MatchingRoomMemberRepository.class);
        MatchingRoomRepository matchingRoomRepository = mock(MatchingRoomRepository.class);
        ChatMessageRepository chatMessageRepository = mock(ChatMessageRepository.class);
        TeamAccountRepository teamAccountRepository = mock(TeamAccountRepository.class);
        TeamHoldingRepository teamHoldingRepository = mock(TeamHoldingRepository.class);
        VoteRepository voteRepository = mock(VoteRepository.class);
        VoteParticipantRepository voteParticipantRepository = mock(VoteParticipantRepository.class);
        KisApiService kisApiService = mock(KisApiService.class);
        ChatRoomService service = new ChatRoomService(
                matchingRoomMemberRepository,
                matchingRoomRepository,
                chatMessageRepository,
                teamAccountRepository,
                teamHoldingRepository,
                voteRepository,
                voteParticipantRepository,
                kisApiService,
                mock(UserMyPagePreferenceRepository.class),
                new ProfileImageUrlService()
        );

        User user = User.builder().id(10L).nickname("tester").build();
        MatchingRoom startedRoom = MatchingRoom.builder()
                .id(3L)
                .name("시작된 방")
                .capacity(3)
                .memberCount(3)
                .status("started")
                .competitionId(7L)
                .createdAt(Instant.parse("2026-05-17T00:00:00Z"))
                .build();
        MatchingRoom waitingRoom = MatchingRoom.builder()
                .id(4L)
                .name("대기 중인 방")
                .capacity(3)
                .memberCount(1)
                .status("waiting")
                .createdAt(Instant.parse("2026-05-17T00:00:00Z"))
                .build();
        MatchingRoom endedRoom = MatchingRoom.builder()
                .id(5L)
                .name("종료된 방")
                .capacity(3)
                .memberCount(3)
                .status("ended")
                .createdAt(Instant.parse("2026-05-16T00:00:00Z"))
                .build();
        MatchingRoomMember startedMembership = MatchingRoomMember.builder()
                .id(1L)
                .matchingRoom(startedRoom)
                .user(user)
                .joinedAt(Instant.parse("2026-05-17T00:10:00Z"))
                .build();
        MatchingRoomMember waitingMembership = MatchingRoomMember.builder()
                .id(2L)
                .matchingRoom(waitingRoom)
                .user(user)
                .joinedAt(Instant.parse("2026-05-17T00:20:00Z"))
                .build();
        MatchingRoomMember endedMembership = MatchingRoomMember.builder()
                .id(3L)
                .matchingRoom(endedRoom)
                .user(user)
                .joinedAt(Instant.parse("2026-05-16T00:10:00Z"))
                .build();

        when(matchingRoomMemberRepository.findByUserIdOrderByJoinedAtDesc(user.getId()))
                .thenReturn(List.of(waitingMembership, startedMembership, endedMembership));
        when(teamAccountRepository.findByTeamId(startedRoom.getId())).thenReturn(Optional.empty());
        when(teamAccountRepository.findByTeamId(endedRoom.getId())).thenReturn(Optional.empty());
        when(teamHoldingRepository.findByTeamId(startedRoom.getId())).thenReturn(List.of());
        when(teamHoldingRepository.findByTeamId(endedRoom.getId())).thenReturn(List.of());
        when(chatMessageRepository.findTopByRoomIdOrderByCreatedAtDesc(startedRoom.getId())).thenReturn(Optional.empty());
        when(chatMessageRepository.findTopByRoomIdOrderByCreatedAtDesc(endedRoom.getId())).thenReturn(Optional.empty());
        when(voteRepository.findByRoomIdOrderByCreatedAtDesc(startedRoom.getId())).thenReturn(List.of());
        when(voteRepository.findByRoomIdOrderByCreatedAtDesc(endedRoom.getId())).thenReturn(List.of());

        List<Map<String, Object>> rooms = service.getMyChatRooms(user);

        assertEquals(2, rooms.size());
        assertEquals(startedRoom.getId(), rooms.get(0).get("roomId"));
        assertEquals("started", rooms.get(0).get("status"));
        assertEquals(7L, rooms.get(0).get("competitionId"));
        assertEquals("TOURNAMENT", rooms.get(0).get("roomType"));
        assertEquals(endedRoom.getId(), rooms.get(1).get("roomId"));
        assertEquals("ended", rooms.get(1).get("status"));
        assertEquals(null, rooms.get(1).get("competitionId"));
        assertEquals("GROUP_INVEST", rooms.get(1).get("roomType"));
        verify(teamAccountRepository, never()).findByTeamId(waitingRoom.getId());
        verify(chatMessageRepository, never()).findTopByRoomIdOrderByCreatedAtDesc(waitingRoom.getId());
        verify(voteRepository, never()).findByRoomIdOrderByCreatedAtDesc(waitingRoom.getId());
    }

    @Test
    void getMyChatRooms_formatsMentionAllLastMessagePreview() {
        MatchingRoomMemberRepository matchingRoomMemberRepository = mock(MatchingRoomMemberRepository.class);
        MatchingRoomRepository matchingRoomRepository = mock(MatchingRoomRepository.class);
        ChatMessageRepository chatMessageRepository = mock(ChatMessageRepository.class);
        TeamAccountRepository teamAccountRepository = mock(TeamAccountRepository.class);
        TeamHoldingRepository teamHoldingRepository = mock(TeamHoldingRepository.class);
        VoteRepository voteRepository = mock(VoteRepository.class);
        VoteParticipantRepository voteParticipantRepository = mock(VoteParticipantRepository.class);
        KisApiService kisApiService = mock(KisApiService.class);
        ChatRoomService service = new ChatRoomService(
                matchingRoomMemberRepository,
                matchingRoomRepository,
                chatMessageRepository,
                teamAccountRepository,
                teamHoldingRepository,
                voteRepository,
                voteParticipantRepository,
                kisApiService,
                mock(UserMyPagePreferenceRepository.class),
                new ProfileImageUrlService()
        );

        User user = User.builder().id(10L).nickname("tester").build();
        MatchingRoom room = MatchingRoom.builder()
                .id(3L)
                .name("테스트방")
                .capacity(3)
                .memberCount(2)
                .status("started")
                .createdAt(Instant.parse("2026-05-17T00:00:00Z"))
                .build();
        MatchingRoomMember membership = MatchingRoomMember.builder()
                .id(1L)
                .matchingRoom(room)
                .user(user)
                .joinedAt(Instant.parse("2026-05-17T00:10:00Z"))
                .build();
        ChatMessage mentionAllMessage = ChatMessage.of(
                room.getId(),
                user.getId(),
                user.getNickname(),
                "{\"type\":\"mention_all\",\"message\":\"모든 팀원을 호출했어요!\",\"callerId\":10,\"callerNickname\":\"tester\"}"
        );
        mentionAllMessage.setId(123L);

        when(matchingRoomMemberRepository.findByUserIdOrderByJoinedAtDesc(user.getId())).thenReturn(List.of(membership));
        when(teamAccountRepository.findByTeamId(room.getId())).thenReturn(Optional.empty());
        when(teamHoldingRepository.findByTeamId(room.getId())).thenReturn(List.of());
        when(chatMessageRepository.findTopByRoomIdOrderByCreatedAtDesc(room.getId())).thenReturn(Optional.of(mentionAllMessage));
        when(voteRepository.findByRoomIdOrderByCreatedAtDesc(room.getId())).thenReturn(List.of());

        List<Map<String, Object>> rooms = service.getMyChatRooms(user);

        @SuppressWarnings("unchecked")
        Map<String, Object> lastMessage = (Map<String, Object>) rooms.get(0).get("lastMessage");
        assertEquals("mention_all", lastMessage.get("type"));
        assertEquals("모든 팀원을 호출했어요!", lastMessage.get("preview"));
        assertEquals("전체 호출", lastMessage.get("title"));
    }

    @Test
    void getMyChatRooms_buildsActiveVoteSummaryFromParticipantRepository() {
        MatchingRoomMemberRepository matchingRoomMemberRepository = mock(MatchingRoomMemberRepository.class);
        MatchingRoomRepository matchingRoomRepository = mock(MatchingRoomRepository.class);
        ChatMessageRepository chatMessageRepository = mock(ChatMessageRepository.class);
        TeamAccountRepository teamAccountRepository = mock(TeamAccountRepository.class);
        TeamHoldingRepository teamHoldingRepository = mock(TeamHoldingRepository.class);
        VoteRepository voteRepository = mock(VoteRepository.class);
        VoteParticipantRepository voteParticipantRepository = mock(VoteParticipantRepository.class);
        KisApiService kisApiService = mock(KisApiService.class);
        ChatRoomService service = new ChatRoomService(
                matchingRoomMemberRepository,
                matchingRoomRepository,
                chatMessageRepository,
                teamAccountRepository,
                teamHoldingRepository,
                voteRepository,
                voteParticipantRepository,
                kisApiService,
                mock(UserMyPagePreferenceRepository.class),
                new ProfileImageUrlService()
        );

        User user = User.builder()
                .id(10L)
                .studentId("20260001")
                .password("pw")
                .nickname("tester")
                .build();
        MatchingRoom room = MatchingRoom.builder()
                .id(3L)
                .name("테스트방")
                .capacity(3)
                .memberCount(2)
                .status("started")
                .visibility("PUBLIC")
                .marketType("KR")
                .matchType("FRIEND")
                .createdAt(Instant.now())
                .build();
        MatchingRoomMember membership = MatchingRoomMember.builder()
                .id(1L)
                .matchingRoom(room)
                .user(user)
                .joinedAt(Instant.now())
                .build();
        Vote vote = Vote.builder()
                .id(287L)
                .roomId(room.getId())
                .proposerId(11L)
                .proposerName("proposer")
                .type("매수")
                .stockName("삼성전자")
                .stockCode("005930")
                .quantity(1)
                .proposedPrice(BigDecimal.valueOf(70000))
                .reason("테스트")
                .createdAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .totalMembers(2)
                .status("ongoing")
                .build();
        vote.setParticipants(new AbstractList<>() {
            @Override
            public VoteParticipant get(int index) {
                throw new AssertionError("Vote.participants should not be lazy-loaded for chat room summaries");
            }

            @Override
            public int size() {
                throw new AssertionError("Vote.participants should not be lazy-loaded for chat room summaries");
            }
        });

        when(matchingRoomMemberRepository.findByUserIdOrderByJoinedAtDesc(user.getId())).thenReturn(List.of(membership));
        when(teamAccountRepository.findByTeamId(room.getId())).thenReturn(Optional.empty());
        when(teamHoldingRepository.findByTeamId(room.getId())).thenReturn(List.of());
        when(chatMessageRepository.findTopByRoomIdOrderByCreatedAtDesc(room.getId())).thenReturn(Optional.empty());
        when(voteRepository.findByRoomIdOrderByCreatedAtDesc(room.getId())).thenReturn(List.of(vote));
        when(voteParticipantRepository.findByVote_IdOrderById(vote.getId())).thenReturn(List.of(
                VoteParticipant.builder()
                        .id(1L)
                        .vote(vote)
                        .userId(user.getId())
                        .userName("tester")
                        .voteChoice("찬성")
                        .build(),
                VoteParticipant.builder()
                        .id(2L)
                        .vote(vote)
                        .userId(11L)
                        .userName("member")
                        .voteChoice("반대")
                        .build()
        ));

        List<Map<String, Object>> rooms = service.getMyChatRooms(user);

        @SuppressWarnings("unchecked")
        Map<String, Object> activeVote = (Map<String, Object>) rooms.get(0).get("activeVote");
        @SuppressWarnings("unchecked")
        Map<String, Object> voteSummary = (Map<String, Object>) activeVote.get("voteSummary");
        assertEquals(1L, voteSummary.get("approveCount"));
        assertEquals(0L, voteSummary.get("holdCount"));
        assertEquals(1L, voteSummary.get("rejectCount"));
        assertEquals(2L, voteSummary.get("participantCount"));
        assertEquals("찬성", voteSummary.get("myVote"));
        verify(voteParticipantRepository).findByVote_IdOrderById(vote.getId());
    }

    @Test
    void getMyChatRooms_countsUnreadMessagesAfterLastReadAtExcludingMyMessages() {
        MatchingRoomMemberRepository matchingRoomMemberRepository = mock(MatchingRoomMemberRepository.class);
        MatchingRoomRepository matchingRoomRepository = mock(MatchingRoomRepository.class);
        ChatMessageRepository chatMessageRepository = mock(ChatMessageRepository.class);
        TeamAccountRepository teamAccountRepository = mock(TeamAccountRepository.class);
        TeamHoldingRepository teamHoldingRepository = mock(TeamHoldingRepository.class);
        VoteRepository voteRepository = mock(VoteRepository.class);
        VoteParticipantRepository voteParticipantRepository = mock(VoteParticipantRepository.class);
        KisApiService kisApiService = mock(KisApiService.class);
        ChatRoomService service = new ChatRoomService(
                matchingRoomMemberRepository,
                matchingRoomRepository,
                chatMessageRepository,
                teamAccountRepository,
                teamHoldingRepository,
                voteRepository,
                voteParticipantRepository,
                kisApiService,
                mock(UserMyPagePreferenceRepository.class),
                new ProfileImageUrlService()
        );

        User user = User.builder().id(10L).nickname("tester").build();
        MatchingRoom room = MatchingRoom.builder()
                .id(3L)
                .name("테스트방")
                .capacity(3)
                .memberCount(2)
                .status("started")
                .createdAt(Instant.parse("2026-05-17T00:00:00Z"))
                .build();
        MatchingRoomMember membership = MatchingRoomMember.builder()
                .id(1L)
                .matchingRoom(room)
                .user(user)
                .joinedAt(Instant.parse("2026-05-17T00:10:00Z"))
                .lastReadAt(Instant.parse("2026-05-17T00:20:00Z"))
                .build();

        when(matchingRoomMemberRepository.findByUserIdOrderByJoinedAtDesc(user.getId())).thenReturn(List.of(membership));
        when(teamAccountRepository.findByTeamId(room.getId())).thenReturn(Optional.empty());
        when(teamHoldingRepository.findByTeamId(room.getId())).thenReturn(List.of());
        when(chatMessageRepository.findTopByRoomIdOrderByCreatedAtDesc(room.getId())).thenReturn(Optional.empty());
        when(voteRepository.findByRoomIdOrderByCreatedAtDesc(room.getId())).thenReturn(List.of());
        when(chatMessageRepository.countByRoomIdAndCreatedAtAfterAndUserIdNot(room.getId(), membership.getLastReadAt(), user.getId()))
                .thenReturn(4L);

        List<Map<String, Object>> rooms = service.getMyChatRooms(user);

        assertEquals(4L, rooms.get(0).get("unreadCount"));
        verify(chatMessageRepository).countByRoomIdAndCreatedAtAfterAndUserIdNot(room.getId(), membership.getLastReadAt(), user.getId());
    }

    @Test
    void getMyChatRooms_usesJoinedAtAsUnreadBaselineWhenRoomHasNeverBeenRead() {
        MatchingRoomMemberRepository matchingRoomMemberRepository = mock(MatchingRoomMemberRepository.class);
        MatchingRoomRepository matchingRoomRepository = mock(MatchingRoomRepository.class);
        ChatMessageRepository chatMessageRepository = mock(ChatMessageRepository.class);
        TeamAccountRepository teamAccountRepository = mock(TeamAccountRepository.class);
        TeamHoldingRepository teamHoldingRepository = mock(TeamHoldingRepository.class);
        VoteRepository voteRepository = mock(VoteRepository.class);
        VoteParticipantRepository voteParticipantRepository = mock(VoteParticipantRepository.class);
        KisApiService kisApiService = mock(KisApiService.class);
        ChatRoomService service = new ChatRoomService(
                matchingRoomMemberRepository,
                matchingRoomRepository,
                chatMessageRepository,
                teamAccountRepository,
                teamHoldingRepository,
                voteRepository,
                voteParticipantRepository,
                kisApiService,
                mock(UserMyPagePreferenceRepository.class),
                new ProfileImageUrlService()
        );

        User user = User.builder().id(10L).nickname("tester").build();
        MatchingRoom room = MatchingRoom.builder()
                .id(3L)
                .name("테스트방")
                .capacity(3)
                .memberCount(2)
                .status("started")
                .createdAt(Instant.parse("2026-05-17T00:00:00Z"))
                .build();
        MatchingRoomMember membership = MatchingRoomMember.builder()
                .id(1L)
                .matchingRoom(room)
                .user(user)
                .joinedAt(Instant.parse("2026-05-17T00:10:00Z"))
                .build();

        when(matchingRoomMemberRepository.findByUserIdOrderByJoinedAtDesc(user.getId())).thenReturn(List.of(membership));
        when(teamAccountRepository.findByTeamId(room.getId())).thenReturn(Optional.empty());
        when(teamHoldingRepository.findByTeamId(room.getId())).thenReturn(List.of());
        when(chatMessageRepository.findTopByRoomIdOrderByCreatedAtDesc(room.getId())).thenReturn(Optional.empty());
        when(voteRepository.findByRoomIdOrderByCreatedAtDesc(room.getId())).thenReturn(List.of());
        when(chatMessageRepository.countByRoomIdAndCreatedAtAfterAndUserIdNot(room.getId(), membership.getJoinedAt(), user.getId()))
                .thenReturn(2L);

        List<Map<String, Object>> rooms = service.getMyChatRooms(user);

        assertEquals(2L, rooms.get(0).get("unreadCount"));
        verify(chatMessageRepository).countByRoomIdAndCreatedAtAfterAndUserIdNot(room.getId(), membership.getJoinedAt(), user.getId());
    }

    @Test
    void markRoomAsReadUpdatesMembershipReadTimestamp() {
        MatchingRoomMemberRepository matchingRoomMemberRepository = mock(MatchingRoomMemberRepository.class);
        MatchingRoomRepository matchingRoomRepository = mock(MatchingRoomRepository.class);
        ChatMessageRepository chatMessageRepository = mock(ChatMessageRepository.class);
        TeamAccountRepository teamAccountRepository = mock(TeamAccountRepository.class);
        TeamHoldingRepository teamHoldingRepository = mock(TeamHoldingRepository.class);
        VoteRepository voteRepository = mock(VoteRepository.class);
        VoteParticipantRepository voteParticipantRepository = mock(VoteParticipantRepository.class);
        KisApiService kisApiService = mock(KisApiService.class);
        ChatRoomService service = new ChatRoomService(
                matchingRoomMemberRepository,
                matchingRoomRepository,
                chatMessageRepository,
                teamAccountRepository,
                teamHoldingRepository,
                voteRepository,
                voteParticipantRepository,
                kisApiService,
                mock(UserMyPagePreferenceRepository.class),
                new ProfileImageUrlService()
        );

        User user = User.builder().id(10L).nickname("tester").build();
        MatchingRoom room = MatchingRoom.builder().id(3L).name("테스트방").build();
        MatchingRoomMember membership = MatchingRoomMember.builder()
                .id(1L)
                .matchingRoom(room)
                .user(user)
                .joinedAt(Instant.parse("2026-05-17T00:10:00Z"))
                .build();

        when(matchingRoomMemberRepository.findByMatchingRoomIdAndUserId(room.getId(), user.getId()))
                .thenReturn(Optional.of(membership));

        service.markRoomAsRead(room.getId(), user);

        verify(matchingRoomMemberRepository).save(membership);
        assertEquals(true, membership.getLastReadAt() != null);
    }

    @Test
    void markRoomAsReadRejectsNonMember() {
        MatchingRoomMemberRepository matchingRoomMemberRepository = mock(MatchingRoomMemberRepository.class);
        MatchingRoomRepository matchingRoomRepository = mock(MatchingRoomRepository.class);
        ChatMessageRepository chatMessageRepository = mock(ChatMessageRepository.class);
        TeamAccountRepository teamAccountRepository = mock(TeamAccountRepository.class);
        TeamHoldingRepository teamHoldingRepository = mock(TeamHoldingRepository.class);
        VoteRepository voteRepository = mock(VoteRepository.class);
        VoteParticipantRepository voteParticipantRepository = mock(VoteParticipantRepository.class);
        KisApiService kisApiService = mock(KisApiService.class);
        ChatRoomService service = new ChatRoomService(
                matchingRoomMemberRepository,
                matchingRoomRepository,
                chatMessageRepository,
                teamAccountRepository,
                teamHoldingRepository,
                voteRepository,
                voteParticipantRepository,
                kisApiService,
                mock(UserMyPagePreferenceRepository.class),
                new ProfileImageUrlService()
        );

        User user = User.builder().id(10L).nickname("tester").build();
        when(matchingRoomMemberRepository.findByMatchingRoomIdAndUserId(3L, user.getId())).thenReturn(Optional.empty());

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.markRoomAsRead(3L, user)
        );
        assertEquals("Chat room access denied", exception.getMessage());

        verify(matchingRoomMemberRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void getChatRoomSummaryIncludesMemberProfileImageUrls() {
        MatchingRoomMemberRepository matchingRoomMemberRepository = mock(MatchingRoomMemberRepository.class);
        MatchingRoomRepository matchingRoomRepository = mock(MatchingRoomRepository.class);
        ChatMessageRepository chatMessageRepository = mock(ChatMessageRepository.class);
        TeamAccountRepository teamAccountRepository = mock(TeamAccountRepository.class);
        TeamHoldingRepository teamHoldingRepository = mock(TeamHoldingRepository.class);
        VoteRepository voteRepository = mock(VoteRepository.class);
        VoteParticipantRepository voteParticipantRepository = mock(VoteParticipantRepository.class);
        KisApiService kisApiService = mock(KisApiService.class);
        UserMyPagePreferenceRepository userMyPagePreferenceRepository = mock(UserMyPagePreferenceRepository.class);
        ChatRoomService service = new ChatRoomService(
                matchingRoomMemberRepository,
                matchingRoomRepository,
                chatMessageRepository,
                teamAccountRepository,
                teamHoldingRepository,
                voteRepository,
                voteParticipantRepository,
                kisApiService,
                userMyPagePreferenceRepository,
                new ProfileImageUrlService()
        );

        User currentUser = User.builder()
                .id(10L)
                .nickname("tester")
                .profileImageUrl("https://cdn.example.com/me.png")
                .build();
        User teammate = User.builder()
                .id(11L)
                .nickname("member")
                .profileImageUrl("https://cdn.example.com/member.png")
                .build();
        MatchingRoom room = MatchingRoom.builder()
                .id(3L)
                .name("테스트방")
                .capacity(3)
                .memberCount(2)
                .status("started")
                .createdAt(Instant.parse("2026-05-17T00:00:00Z"))
                .build();
        MatchingRoomMember currentMembership = MatchingRoomMember.builder()
                .id(1L)
                .matchingRoom(room)
                .user(currentUser)
                .joinedAt(Instant.parse("2026-05-17T00:10:00Z"))
                .build();
        MatchingRoomMember teammateMembership = MatchingRoomMember.builder()
                .id(2L)
                .matchingRoom(room)
                .user(teammate)
                .joinedAt(Instant.parse("2026-05-17T00:11:00Z"))
                .build();

        when(matchingRoomMemberRepository.existsByMatchingRoomIdAndUserId(room.getId(), currentUser.getId())).thenReturn(true);
        when(matchingRoomRepository.findById(room.getId())).thenReturn(Optional.of(room));
        when(matchingRoomMemberRepository.findByMatchingRoomIdWithUser(room.getId()))
                .thenReturn(List.of(currentMembership, teammateMembership));
        when(teamAccountRepository.findByTeamId(room.getId())).thenReturn(Optional.empty());
        when(teamHoldingRepository.findByTeamId(room.getId())).thenReturn(List.of());
        when(voteRepository.findByRoomIdOrderByCreatedAtDesc(room.getId())).thenReturn(List.of());
        when(chatMessageRepository.findTopByRoomIdOrderByCreatedAtDesc(room.getId())).thenReturn(Optional.empty());
        when(matchingRoomMemberRepository.findByMatchingRoomIdAndUserId(room.getId(), currentUser.getId()))
                .thenReturn(Optional.of(currentMembership));
        when(userMyPagePreferenceRepository.findById(currentUser.getId())).thenReturn(Optional.empty());
        when(userMyPagePreferenceRepository.findById(teammate.getId())).thenReturn(Optional.empty());

        Map<String, Object> response = service.getChatRoomSummary(room.getId(), currentUser);

        @SuppressWarnings("unchecked")
        Map<String, Object> roomInfo = (Map<String, Object>) response.get("roomInfo");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> members = (List<Map<String, Object>>) roomInfo.get("members");
        String expectedDefaultProfileImageUrl = "https://uniportbe-production.up.railway.app/assets/mypage/profile-options/seed.png";
        assertEquals(expectedDefaultProfileImageUrl, members.get(0).get("profileImageUrl"));
        assertEquals(expectedDefaultProfileImageUrl, members.get(1).get("profileImageUrl"));
    }

    @Test
    void getChatRoomSummaryPrefersSelectedCharacterProfileImageUrls() {
        MatchingRoomMemberRepository matchingRoomMemberRepository = mock(MatchingRoomMemberRepository.class);
        MatchingRoomRepository matchingRoomRepository = mock(MatchingRoomRepository.class);
        ChatMessageRepository chatMessageRepository = mock(ChatMessageRepository.class);
        TeamAccountRepository teamAccountRepository = mock(TeamAccountRepository.class);
        TeamHoldingRepository teamHoldingRepository = mock(TeamHoldingRepository.class);
        VoteRepository voteRepository = mock(VoteRepository.class);
        VoteParticipantRepository voteParticipantRepository = mock(VoteParticipantRepository.class);
        KisApiService kisApiService = mock(KisApiService.class);
        UserMyPagePreferenceRepository userMyPagePreferenceRepository = mock(UserMyPagePreferenceRepository.class);
        ChatRoomService service = new ChatRoomService(
                matchingRoomMemberRepository,
                matchingRoomRepository,
                chatMessageRepository,
                teamAccountRepository,
                teamHoldingRepository,
                voteRepository,
                voteParticipantRepository,
                kisApiService,
                userMyPagePreferenceRepository,
                new ProfileImageUrlService()
        );

        User currentUser = User.builder()
                .id(10L)
                .nickname("tester")
                .profileImageUrl("http://k.kakaocdn.net/legacy.jpg")
                .build();
        User teammate = User.builder()
                .id(11L)
                .nickname("member")
                .profileImageUrl("")
                .build();
        MatchingRoom room = MatchingRoom.builder()
                .id(3L)
                .name("테스트방")
                .capacity(3)
                .memberCount(2)
                .status("started")
                .createdAt(Instant.parse("2026-05-17T00:00:00Z"))
                .build();
        MatchingRoomMember currentMembership = MatchingRoomMember.builder()
                .id(1L)
                .matchingRoom(room)
                .user(currentUser)
                .joinedAt(Instant.parse("2026-05-17T00:10:00Z"))
                .build();
        MatchingRoomMember teammateMembership = MatchingRoomMember.builder()
                .id(2L)
                .matchingRoom(room)
                .user(teammate)
                .joinedAt(Instant.parse("2026-05-17T00:11:00Z"))
                .build();

        when(matchingRoomMemberRepository.existsByMatchingRoomIdAndUserId(room.getId(), currentUser.getId())).thenReturn(true);
        when(matchingRoomRepository.findById(room.getId())).thenReturn(Optional.of(room));
        when(matchingRoomMemberRepository.findByMatchingRoomIdWithUser(room.getId()))
                .thenReturn(List.of(currentMembership, teammateMembership));
        when(teamAccountRepository.findByTeamId(room.getId())).thenReturn(Optional.empty());
        when(teamHoldingRepository.findByTeamId(room.getId())).thenReturn(List.of());
        when(voteRepository.findByRoomIdOrderByCreatedAtDesc(room.getId())).thenReturn(List.of());
        when(chatMessageRepository.findTopByRoomIdOrderByCreatedAtDesc(room.getId())).thenReturn(Optional.empty());
        when(matchingRoomMemberRepository.findByMatchingRoomIdAndUserId(room.getId(), currentUser.getId()))
                .thenReturn(Optional.of(currentMembership));
        when(userMyPagePreferenceRepository.findById(currentUser.getId())).thenReturn(Optional.of(
                UserMyPagePreference.builder().userId(currentUser.getId()).selectedCharacterCode("FOX").build()
        ));
        when(userMyPagePreferenceRepository.findById(teammate.getId())).thenReturn(Optional.of(
                UserMyPagePreference.builder().userId(teammate.getId()).selectedCharacterCode("SEED").build()
        ));

        Map<String, Object> response = service.getChatRoomSummary(room.getId(), currentUser);

        @SuppressWarnings("unchecked")
        Map<String, Object> roomInfo = (Map<String, Object>) response.get("roomInfo");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> members = (List<Map<String, Object>>) roomInfo.get("members");
        assertEquals("https://uniportbe-production.up.railway.app/assets/mypage/profile-options/fox.png", members.get(0).get("profileImageUrl"));
        assertEquals("https://uniportbe-production.up.railway.app/assets/mypage/profile-options/seed.png", members.get(1).get("profileImageUrl"));
    }

    @Test
    void renameChatRoomTrimsNameAndReturnsRoomIdentity() {
        MatchingRoomMemberRepository matchingRoomMemberRepository = mock(MatchingRoomMemberRepository.class);
        MatchingRoomRepository matchingRoomRepository = mock(MatchingRoomRepository.class);
        ChatMessageRepository chatMessageRepository = mock(ChatMessageRepository.class);
        TeamAccountRepository teamAccountRepository = mock(TeamAccountRepository.class);
        TeamHoldingRepository teamHoldingRepository = mock(TeamHoldingRepository.class);
        VoteRepository voteRepository = mock(VoteRepository.class);
        VoteParticipantRepository voteParticipantRepository = mock(VoteParticipantRepository.class);
        KisApiService kisApiService = mock(KisApiService.class);
        ChatRoomService service = new ChatRoomService(
                matchingRoomMemberRepository,
                matchingRoomRepository,
                chatMessageRepository,
                teamAccountRepository,
                teamHoldingRepository,
                voteRepository,
                voteParticipantRepository,
                kisApiService,
                mock(UserMyPagePreferenceRepository.class),
                new ProfileImageUrlService()
        );

        User user = User.builder().id(10L).nickname("tester").build();
        MatchingRoom room = MatchingRoom.builder().id(3L).name("이전 이름").build();
        when(matchingRoomRepository.findById(room.getId())).thenReturn(Optional.of(room));
        when(matchingRoomMemberRepository.existsByMatchingRoomIdAndUserId(room.getId(), user.getId())).thenReturn(true);

        Map<String, Object> response = service.renameChatRoom(room.getId(), user, "  새 채팅방 이름  ");

        assertEquals(room.getId(), response.get("roomId"));
        assertEquals(room.getId(), response.get("groupId"));
        assertEquals("새 채팅방 이름", response.get("name"));
        assertEquals("새 채팅방 이름 채팅방", response.get("title"));
        assertEquals("새 채팅방 이름", room.getName());
        verify(matchingRoomRepository).save(room);
    }

    @Test
    void renameChatRoomRejectsBlankName() {
        ChatRoomService service = createChatRoomService(
                mock(MatchingRoomMemberRepository.class),
                mock(MatchingRoomRepository.class)
        );
        User user = User.builder().id(10L).nickname("tester").build();

        ApiException exception = assertThrows(
                ApiException.class,
                () -> service.renameChatRoom(3L, user, "   ")
        );

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatus());
    }

    @Test
    void renameChatRoomRejectsNameLongerThanThirtyCharacters() {
        ChatRoomService service = createChatRoomService(
                mock(MatchingRoomMemberRepository.class),
                mock(MatchingRoomRepository.class)
        );
        User user = User.builder().id(10L).nickname("tester").build();

        ApiException exception = assertThrows(
                ApiException.class,
                () -> service.renameChatRoom(3L, user, "가".repeat(31))
        );

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatus());
    }

    @Test
    void renameChatRoomReturnsNotFoundWhenRoomIsMissing() {
        MatchingRoomMemberRepository matchingRoomMemberRepository = mock(MatchingRoomMemberRepository.class);
        MatchingRoomRepository matchingRoomRepository = mock(MatchingRoomRepository.class);
        ChatRoomService service = createChatRoomService(matchingRoomMemberRepository, matchingRoomRepository);
        User user = User.builder().id(10L).nickname("tester").build();
        when(matchingRoomRepository.findById(3L)).thenReturn(Optional.empty());

        ApiException exception = assertThrows(
                ApiException.class,
                () -> service.renameChatRoom(3L, user, "새 이름")
        );

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatus());
        verify(matchingRoomMemberRepository, never()).existsByMatchingRoomIdAndUserId(3L, user.getId());
    }

    @Test
    void renameChatRoomRejectsNonMember() {
        MatchingRoomMemberRepository matchingRoomMemberRepository = mock(MatchingRoomMemberRepository.class);
        MatchingRoomRepository matchingRoomRepository = mock(MatchingRoomRepository.class);
        ChatRoomService service = createChatRoomService(matchingRoomMemberRepository, matchingRoomRepository);
        User user = User.builder().id(10L).nickname("tester").build();
        MatchingRoom room = MatchingRoom.builder().id(3L).name("이전 이름").build();
        when(matchingRoomRepository.findById(room.getId())).thenReturn(Optional.of(room));
        when(matchingRoomMemberRepository.existsByMatchingRoomIdAndUserId(room.getId(), user.getId())).thenReturn(false);

        ApiException exception = assertThrows(
                ApiException.class,
                () -> service.renameChatRoom(room.getId(), user, "새 이름")
        );

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatus());
        verify(matchingRoomRepository, never()).save(room);
    }

    @Test
    void renameChatRoomRejectsEndedRoom() {
        MatchingRoomMemberRepository matchingRoomMemberRepository = mock(MatchingRoomMemberRepository.class);
        MatchingRoomRepository matchingRoomRepository = mock(MatchingRoomRepository.class);
        ChatRoomService service = createChatRoomService(matchingRoomMemberRepository, matchingRoomRepository);
        User user = User.builder().id(10L).nickname("tester").build();
        MatchingRoom room = MatchingRoom.builder()
                .id(3L)
                .name("종료된 방")
                .status("ended")
                .build();
        when(matchingRoomRepository.findById(room.getId())).thenReturn(Optional.of(room));
        when(matchingRoomMemberRepository.existsByMatchingRoomIdAndUserId(room.getId(), user.getId())).thenReturn(true);

        ApiException exception = assertThrows(
                ApiException.class,
                () -> service.renameChatRoom(room.getId(), user, "새 이름")
        );

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatus());
        assertEquals("종료된 채팅방은 보기만 할 수 있습니다.", exception.getMessage());
        verify(matchingRoomRepository, never()).save(room);
    }

    private ChatRoomService createChatRoomService(MatchingRoomMemberRepository matchingRoomMemberRepository,
                                                  MatchingRoomRepository matchingRoomRepository) {
        return new ChatRoomService(
                matchingRoomMemberRepository,
                matchingRoomRepository,
                mock(ChatMessageRepository.class),
                mock(TeamAccountRepository.class),
                mock(TeamHoldingRepository.class),
                mock(VoteRepository.class),
                mock(VoteParticipantRepository.class),
                mock(KisApiService.class),
                mock(UserMyPagePreferenceRepository.class),
                new ProfileImageUrlService()
        );
    }
}

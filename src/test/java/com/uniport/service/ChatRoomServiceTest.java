package com.uniport.service;

import com.uniport.entity.MatchingRoom;
import com.uniport.entity.MatchingRoomMember;
import com.uniport.entity.User;
import com.uniport.entity.Vote;
import com.uniport.entity.VoteParticipant;
import com.uniport.repository.ChatMessageRepository;
import com.uniport.repository.MatchingRoomMemberRepository;
import com.uniport.repository.MatchingRoomRepository;
import com.uniport.repository.TeamAccountRepository;
import com.uniport.repository.TeamHoldingRepository;
import com.uniport.repository.VoteParticipantRepository;
import com.uniport.repository.VoteRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.AbstractList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChatRoomServiceTest {

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
                kisApiService
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
}

package com.uniport.service;

import com.uniport.entity.MatchingRoom;
import com.uniport.entity.MatchingRoomMember;
import com.uniport.entity.User;
import com.uniport.exception.ApiException;
import com.uniport.repository.MatchingRoomMemberRepository;
import com.uniport.repository.MatchingRoomRepository;
import com.uniport.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 매칭방 생성·목록·참가·나가기·시작. 참가자는 (방, 사용자) 단위로 저장되어 중복 참여 불가.
 */
@Service
public class MatchingRoomService {

    private static final String ROOM_ID_PREFIX = "room-";

    private final MatchingRoomRepository matchingRoomRepository;
    private final MatchingRoomMemberRepository matchingRoomMemberRepository;
    private final UserRepository userRepository;

    public MatchingRoomService(MatchingRoomRepository matchingRoomRepository,
                              MatchingRoomMemberRepository matchingRoomMemberRepository,
                              UserRepository userRepository) {
        this.matchingRoomRepository = matchingRoomRepository;
        this.matchingRoomMemberRepository = matchingRoomMemberRepository;
        this.userRepository = userRepository;
    }

    /** 방 목록. user가 있으면 각 방에 isJoined(현재 사용자 참가 여부) 포함. */
    public List<Map<String, Object>> list(User user) {
        List<MatchingRoom> rooms = matchingRoomRepository.findAllByOrderByCreatedAtDesc();
        if (user == null) {
            return rooms.stream().map(this::toMap).collect(Collectors.toList());
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (MatchingRoom room : rooms) {
            boolean isJoined = matchingRoomMemberRepository.existsByMatchingRoomIdAndUserId(room.getId(), user.getId());
            result.add(toMapWithJoined(room, isJoined));
        }
        return result;
    }

    /** 해당 멤버가 모의투자를 시작했는지 (참가 중인 방 중 status가 "started"인 방이 있는지). */
    public boolean hasUserStartedMockTrading(User user) {
        if (user == null || user.getId() == null) return false;
        return matchingRoomMemberRepository.existsByUserIdAndMatchingRoom_Status(user.getId(), "started");
    }

    /** 현재 사용자가 참가 중인 방 목록 (최신 참가 순). */
    public List<Map<String, Object>> listRoomsJoinedBy(User user) {
        return matchingRoomMemberRepository.findByUserIdOrderByJoinedAtDesc(user.getId()).stream()
                .map(m -> toMap(m.getMatchingRoom()))
                .collect(Collectors.toList());
    }

    /** 방 생성. creator가 있으면 해당 멤버를 방에 자동 추가. 이미 참가 중인 방이 있으면 생성 불가. */
    @Transactional
    public Map<String, Object> create(String name, User creator) {
        if (creator != null && !matchingRoomMemberRepository.findByUserIdOrderByJoinedAtDesc(creator.getId()).isEmpty()) {
            throw new ApiException("이미 참가 중인 방이 있습니다. 새 방을 만들려면 먼저 방을 나가세요.", HttpStatus.BAD_REQUEST);
        }
        MatchingRoom room = MatchingRoom.create(name);
        room = matchingRoomRepository.save(room);
        if (creator != null) {
            matchingRoomMemberRepository.save(MatchingRoomMember.of(room, creator));
            room.setMemberCount((int) matchingRoomMemberRepository.countByMatchingRoomId(room.getId()));
            matchingRoomRepository.save(room);
        }
        return Map.of(
                "success", true,
                "message", "Created",
                "room", toMap(room)
        );
    }

    @Transactional
    public Map<String, Object> join(String roomId, User user) {
        MatchingRoom room = findRoomByApiId(roomId);
        if (matchingRoomMemberRepository.existsByMatchingRoomIdAndUserId(room.getId(), user.getId())) {
            throw new ApiException("이미 참가 중인 방입니다.", HttpStatus.BAD_REQUEST);
        }
        long currentCount = matchingRoomMemberRepository.countByMatchingRoomId(room.getId());
        if (currentCount >= room.getCapacity()) {
            throw new ApiException("방이 가득 찼습니다.", HttpStatus.BAD_REQUEST);
        }
        matchingRoomMemberRepository.save(MatchingRoomMember.of(room, user));
        room.setMemberCount((int) matchingRoomMemberRepository.countByMatchingRoomId(room.getId()));
        matchingRoomRepository.save(room);
        return Map.of(
                "success", true,
                "message", "Joined",
                "room", Map.of("id", toApiId(room.getId()), "memberCount", room.getMemberCount())
        );
    }

    @Transactional
    public Map<String, Object> leave(String roomId, User user) {
        MatchingRoom room = findRoomByApiId(roomId);
        if (!matchingRoomMemberRepository.existsByMatchingRoomIdAndUserId(room.getId(), user.getId())) {
            throw new ApiException("참가 중인 방이 아닙니다.", HttpStatus.BAD_REQUEST);
        }
        matchingRoomMemberRepository.deleteByMatchingRoomIdAndUserId(room.getId(), user.getId());
        // 방을 나가면 팀 소속 해제 → 이후 주문 불가 until 다른 방에서 시작
        user.setTeamId(null);
        userRepository.save(user);
        int newCount = (int) matchingRoomMemberRepository.countByMatchingRoomId(room.getId());
        room.setMemberCount(newCount);
        matchingRoomRepository.save(room);
        if (newCount == 0) {
            matchingRoomRepository.delete(room);
        }
        return Map.of("success", true, "message", "Left");
    }

    @Transactional
    public Map<String, Object> start(String roomId) {
        MatchingRoom room = findRoomByApiId(roomId);
        room.setStatus("started");
        matchingRoomRepository.save(room);
        // 방이 시작되면 이 방 멤버들의 팀을 이 방으로 고정 → 주문/보유가 이 팀(groupId)에 쌓임
        String teamIdStr = "team-" + room.getId();
        List<MatchingRoomMember> members = matchingRoomMemberRepository.findByMatchingRoomIdWithUser(room.getId());
        for (MatchingRoomMember m : members) {
            User u = m.getUser();
            if (u != null) {
                u.setTeamId(teamIdStr);
                userRepository.save(u);
            }
        }
        return Map.of(
                "success", true,
                "message", "Started",
                "teamId", teamIdStr,
                "competitionId", 1
        );
    }

    /** 관리자: 팀(매칭방) 삭제. 소속 멤버 전부 삭제 후 방 삭제. */
    @Transactional
    public Map<String, Object> deleteRoomByAdmin(String roomId) {
        MatchingRoom room = findRoomByApiIdFlexible(roomId);
        matchingRoomMemberRepository.deleteByMatchingRoom_Id(room.getId());
        matchingRoomRepository.delete(room);
        return Map.of("success", true, "message", "팀(매칭방)이 삭제되었습니다.");
    }

    /** 관리자: 팀(매칭방)에서 멤버 강제 제거. */
    @Transactional
    public Map<String, Object> removeMemberByAdmin(String roomId, Long userId) {
        MatchingRoom room = findRoomByApiIdFlexible(roomId);
        if (!matchingRoomMemberRepository.existsByMatchingRoomIdAndUserId(room.getId(), userId)) {
            throw new ApiException("해당 팀에 속한 멤버가 아닙니다.", HttpStatus.NOT_FOUND);
        }
        matchingRoomMemberRepository.deleteByMatchingRoomIdAndUserId(room.getId(), userId);
        int newCount = (int) matchingRoomMemberRepository.countByMatchingRoomId(room.getId());
        room.setMemberCount(newCount);
        matchingRoomRepository.save(room);
        if (newCount == 0) {
            matchingRoomRepository.delete(room);
        }
        return Map.of("success", true, "message", "멤버가 팀에서 제거되었습니다.");
    }

    private MatchingRoom findRoomByApiId(String roomId) {
        Long id = parseRoomId(roomId);
        return matchingRoomRepository.findById(id)
                .orElseThrow(() -> new ApiException("방을 찾을 수 없습니다.", HttpStatus.NOT_FOUND));
    }

    private MatchingRoom findRoomByApiIdFlexible(String roomId) {
        Long id = parseRoomIdFlexible(roomId);
        return matchingRoomRepository.findById(id)
                .orElseThrow(() -> new ApiException("방을 찾을 수 없습니다.", HttpStatus.NOT_FOUND));
    }

    private static Long parseRoomIdFlexible(String roomId) {
        if (roomId == null || roomId.isBlank()) {
            throw new ApiException("방 ID가 필요합니다.", HttpStatus.BAD_REQUEST);
        }
        String s = roomId.trim();
        if (s.startsWith(ROOM_ID_PREFIX)) {
            try {
                return Long.parseLong(s.substring(ROOM_ID_PREFIX.length()));
            } catch (NumberFormatException e) {
                throw new ApiException("잘못된 방 ID입니다.", HttpStatus.BAD_REQUEST);
            }
        }
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException e) {
            throw new ApiException("잘못된 방 ID입니다.", HttpStatus.BAD_REQUEST);
        }
    }

    private static Long parseRoomId(String roomId) {
        if (roomId == null || !roomId.startsWith(ROOM_ID_PREFIX)) {
            throw new ApiException("잘못된 방 ID입니다.", HttpStatus.BAD_REQUEST);
        }
        try {
            return Long.parseLong(roomId.substring(ROOM_ID_PREFIX.length()));
        } catch (NumberFormatException e) {
            throw new ApiException("잘못된 방 ID입니다.", HttpStatus.BAD_REQUEST);
        }
    }

    private Map<String, Object> toMap(MatchingRoom room) {
        int memberCount = (int) matchingRoomMemberRepository.countByMatchingRoomId(room.getId());
        List<Map<String, Object>> membersList = matchingRoomMemberRepository.findByMatchingRoomIdWithUser(room.getId()).stream()
                .map(m -> {
                    var u = m.getUser();
                    return Map.<String, Object>of(
                            "id", u.getId() != null ? u.getId().toString() : "",
                            "nickname", u.getNickname() != null ? u.getNickname() : ""
                    );
                })
                .collect(Collectors.toList());
        Map<String, Object> map = new HashMap<>();
        map.put("id", toApiId(room.getId()));
        map.put("name", room.getName());
        map.put("capacity", room.getCapacity());
        map.put("memberCount", memberCount);
        map.put("members", membersList);
        map.put("status", room.getStatus());
        map.put("createdAt", room.getCreatedAt().toString());
        return map;
    }

    private Map<String, Object> toMapWithJoined(MatchingRoom room, boolean isJoined) {
        Map<String, Object> map = new HashMap<>(toMap(room));
        map.put("isJoined", isJoined);
        return map;
    }

    private static String toApiId(Long id) {
        return id != null ? ROOM_ID_PREFIX + id : null;
    }
}

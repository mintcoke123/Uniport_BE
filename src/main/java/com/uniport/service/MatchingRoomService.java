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

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 매칭방 생성·목록·참가·나가기·시작. 참가자는 (방, 사용자) 단위로 저장되어 중복 참여 불가.
 */
@Service
public class MatchingRoomService {

    private static final String ROOM_ID_PREFIX = "room-";
    private static final String VISIBILITY_PUBLIC = "PUBLIC";
    private static final String VISIBILITY_PRIVATE = "PRIVATE";
    private static final String BASE62 = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
    private static final int INVITE_CODE_LENGTH = 8;
    private static final int INVITE_CODE_MAX_RETRIES = 10;
    private static final SecureRandom RANDOM = new SecureRandom();

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

    /**
     * 개인방(capacity=1)에서는 채팅/투표를 사용할 수 없도록 가드.
     * groupId = MatchingRoom.id 에 대해 capacity == 1 이면 ApiException(403) 발생.
     */
    public void assertTeamRoom(Long groupId) {
        matchingRoomRepository.findById(groupId)
                .filter(room -> room.getCapacity() == 1)
                .ifPresent(room -> {
                    throw new ApiException("개인방에서는 채팅/투표를 사용할 수 없습니다.", HttpStatus.FORBIDDEN);
                });
    }

    /**
     * 투표 생성용: 개인방(capacity=1)에서는 지정가/조건부만 허용. 시장가 투표는 assertTeamRoom과 동일하게 403.
     */
    public void assertTeamRoomForVoteCreate(Long groupId, String orderStrategy) {
        java.util.Optional<MatchingRoom> roomOpt = matchingRoomRepository.findById(groupId);
        if (roomOpt.isEmpty()) return;
        MatchingRoom room = roomOpt.get();
        if (room.getCapacity() != 1) return;
        String strategy = (orderStrategy != null && !orderStrategy.isBlank()) ? orderStrategy.trim().toUpperCase() : "MARKET";
        if ("LIMIT".equals(strategy) || "CONDITIONAL".equals(strategy)) {
            return;
        }
        throw new ApiException("개인방에서는 채팅/투표를 사용할 수 없습니다.", HttpStatus.FORBIDDEN);
    }

    /** 방 목록. PUBLIC/PRIVATE 모두 반환. user가 있으면 각 방에 isJoined 포함. 비공개 방은 목록에 보이지만 참가는 초대코드로만 가능. */
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

    /** 방 생성. visibility 없으면 PUBLIC. capacity 없으면 3(팀). creator가 있으면 해당 멤버를 방에 자동 추가. */
    @Transactional
    public Map<String, Object> create(String name, String visibility, Integer capacity, User creator) {
        if (creator != null && !matchingRoomMemberRepository.findByUserIdOrderByJoinedAtDesc(creator.getId()).isEmpty()) {
            throw new ApiException("이미 참가 중인 방이 있습니다. 새 방을 만들려면 먼저 방을 나가세요.", HttpStatus.BAD_REQUEST);
        }
        String vis = (visibility != null && !visibility.isBlank()) ? visibility.trim() : VISIBILITY_PUBLIC;
        if (!VISIBILITY_PUBLIC.equals(vis) && !VISIBILITY_PRIVATE.equals(vis)) {
            vis = VISIBILITY_PUBLIC;
        }
        int cap = (capacity != null && capacity >= 1 && capacity <= 10) ? capacity : 3;
        MatchingRoom room = MatchingRoom.create(name, cap);
        room.setVisibility(vis);
        room = matchingRoomRepository.save(room);
        String code = generateUniqueInviteCode();
        room.setInviteCode(code);
        matchingRoomRepository.save(room);
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
        if (VISIBILITY_PRIVATE.equals(room.getVisibility())) {
            throw new ApiException("비공개 방은 초대코드로만 입장 가능합니다.", HttpStatus.FORBIDDEN);
        }
        return doJoin(room, user);
    }

    /** 초대코드로 입장. PUBLIC/PRIVATE 모두 허용(코드 입력 한 가지로 통일). */
    @Transactional
    public Map<String, Object> joinByCode(String inviteCode, User user) {
        if (inviteCode == null || inviteCode.isBlank()) {
            throw new ApiException("초대코드를 입력해 주세요.", HttpStatus.BAD_REQUEST);
        }
        MatchingRoom room = matchingRoomRepository.findByInviteCode(inviteCode.trim())
                .orElseThrow(() -> new ApiException("유효하지 않은 초대코드입니다.", HttpStatus.NOT_FOUND));
        return doJoin(room, user);
    }

    private Map<String, Object> doJoin(MatchingRoom room, User user) {
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
        long memberCount = matchingRoomMemberRepository.countByMatchingRoomId(room.getId());
        if (memberCount == 0) {
            throw new ApiException("방에 멤버가 없어 시작할 수 없습니다.", HttpStatus.BAD_REQUEST);
        }
        if ("started".equals(room.getStatus())) {
            return Map.of(
                    "success", true,
                    "message", "Started",
                    "teamId", "team-" + room.getId(),
                    "competitionId", 1
            );
        }
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

    private String generateUniqueInviteCode() {
        for (int i = 0; i < INVITE_CODE_MAX_RETRIES; i++) {
            StringBuilder sb = new StringBuilder(INVITE_CODE_LENGTH);
            for (int j = 0; j < INVITE_CODE_LENGTH; j++) {
                sb.append(BASE62.charAt(RANDOM.nextInt(BASE62.length())));
            }
            String code = sb.toString();
            if (matchingRoomRepository.findByInviteCode(code).isEmpty()) {
                return code;
            }
        }
        throw new ApiException("초대코드 생성에 실패했습니다. 잠시 후 다시 시도해 주세요.", HttpStatus.INTERNAL_SERVER_ERROR);
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
        map.put("visibility", room.getVisibility() != null ? room.getVisibility() : VISIBILITY_PUBLIC);
        map.put("inviteCode", room.getInviteCode());
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

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
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

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
    private final Map<Long, List<Long>> pendingInviteUserIdsByRoomId = new ConcurrentHashMap<>();

    public MatchingRoomService(MatchingRoomRepository matchingRoomRepository,
                               MatchingRoomMemberRepository matchingRoomMemberRepository,
                               UserRepository userRepository) {
        this.matchingRoomRepository = matchingRoomRepository;
        this.matchingRoomMemberRepository = matchingRoomMemberRepository;
        this.userRepository = userRepository;
    }

    public void assertTeamRoom(Long groupId) {
        matchingRoomRepository.findById(groupId)
                .filter(room -> room.getCapacity() == 1)
                .ifPresent(room -> {
                    throw new ApiException("개인방에서는 채팅/투표를 사용할 수 없습니다.", HttpStatus.FORBIDDEN);
                });
    }

    public void assertTeamRoomForVoteCreate(Long groupId, String orderStrategy) {
        var roomOpt = matchingRoomRepository.findById(groupId);
        if (roomOpt.isEmpty()) return;
        MatchingRoom room = roomOpt.get();
        if (room.getCapacity() != 1) return;

        String strategy = orderStrategy != null && !orderStrategy.isBlank()
                ? orderStrategy.trim().toUpperCase()
                : "MARKET";
        if ("LIMIT".equals(strategy) || "CONDITIONAL".equals(strategy)) {
            return;
        }
        throw new ApiException("개인방에서는 채팅/투표를 사용할 수 없습니다.", HttpStatus.FORBIDDEN);
    }

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

    public boolean hasUserStartedMockTrading(User user) {
        if (user == null || user.getId() == null) return false;
        return matchingRoomMemberRepository.existsByUserIdAndMatchingRoom_Status(user.getId(), "started");
    }

    public List<Map<String, Object>> listRoomsJoinedBy(User user) {
        return matchingRoomMemberRepository.findByUserIdOrderByJoinedAtDesc(user.getId()).stream()
                .map(m -> toMap(m.getMatchingRoom()))
                .collect(Collectors.toList());
    }

    @Transactional
    public Map<String, Object> create(String name, String visibility, Integer capacity, User creator) {
        return create(name, visibility, capacity, null, null, null, creator);
    }

    @Transactional
    public Map<String, Object> create(String name, String visibility, Integer capacity,
                                      String matchType, String marketType, List<Long> inviteeUserIds, User creator) {
        if (creator != null && creator.getId() != null
                && !matchingRoomMemberRepository.findByUserIdOrderByJoinedAtDesc(creator.getId()).isEmpty()) {
            throw new ApiException("이미 참여 중인 방이 있습니다. 새 방을 만들려면 먼저 현재 방에서 나가야 합니다.", HttpStatus.BAD_REQUEST);
        }

        String vis = normalizeVisibility(visibility);
        int cap = (capacity != null && capacity >= 1 && capacity <= 10) ? capacity : 3;
        String resolvedMatchType = normalizeMatchType(matchType);
        String resolvedMarketType = normalizeMarketType(marketType);

        MatchingRoom room = MatchingRoom.create(name, cap);
        room.setVisibility(vis);
        room.setMatchType(resolvedMatchType);
        room.setMarketType(resolvedMarketType);
        room = matchingRoomRepository.save(room);

        room.setInviteCode(generateUniqueInviteCode());
        room = matchingRoomRepository.save(room);

        List<Long> invitedUserIds = inviteeUserIds != null ? new ArrayList<>(inviteeUserIds) : new ArrayList<>();
        pendingInviteUserIdsByRoomId.put(room.getId(), invitedUserIds);

        if (creator != null) {
            matchingRoomMemberRepository.save(MatchingRoomMember.of(room, creator));
            room.setMemberCount((int) matchingRoomMemberRepository.countByMatchingRoomId(room.getId()));
            room = matchingRoomRepository.save(room);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", "Created");
        result.put("room", toMap(room));
        result.put("detail", getRoomDetail(toApiId(room.getId()), creator));
        return result;
    }

    @Transactional
    public Map<String, Object> join(String roomId, User user) {
        MatchingRoom room = findRoomByApiId(roomId);
        if (VISIBILITY_PRIVATE.equals(room.getVisibility())) {
            throw new ApiException("비공개 방은 초대 코드로만 입장 가능합니다.", HttpStatus.FORBIDDEN);
        }
        return doJoin(room, user);
    }

    @Transactional
    public Map<String, Object> joinByCode(String inviteCode, User user) {
        if (inviteCode == null || inviteCode.isBlank()) {
            throw new ApiException("초대 코드를 입력해 주세요.", HttpStatus.BAD_REQUEST);
        }
        MatchingRoom room = matchingRoomRepository.findByInviteCode(inviteCode.trim())
                .orElseThrow(() -> new ApiException("유효하지 않은 초대 코드입니다.", HttpStatus.NOT_FOUND));
        return doJoin(room, user);
    }

    @Transactional
    public Map<String, Object> leave(String roomId, User user) {
        MatchingRoom room = findRoomByApiId(roomId);
        if (!matchingRoomMemberRepository.existsByMatchingRoomIdAndUserId(room.getId(), user.getId())) {
            throw new ApiException("참여 중인 방이 아닙니다.", HttpStatus.BAD_REQUEST);
        }

        matchingRoomMemberRepository.deleteByMatchingRoomIdAndUserId(room.getId(), user.getId());
        user.setTeamId(null);
        userRepository.save(user);

        int newCount = (int) matchingRoomMemberRepository.countByMatchingRoomId(room.getId());
        room.setMemberCount(newCount);
        matchingRoomRepository.save(room);

        if (newCount == 0) {
            pendingInviteUserIdsByRoomId.remove(room.getId());
            matchingRoomRepository.delete(room);
        }
        return Map.of("success", true, "message", "Left");
    }

    @Transactional
    public Map<String, Object> start(String roomId, User user) {
        MatchingRoom room = findRoomByApiId(roomId);
        if (!matchingRoomMemberRepository.existsByMatchingRoomIdAndUserId(room.getId(), user.getId())) {
            throw new ApiException("해당 방의 참가자만 시작할 수 있습니다.", HttpStatus.FORBIDDEN);
        }

        long memberCount = matchingRoomMemberRepository.countByMatchingRoomId(room.getId());
        if (memberCount == 0) {
            throw new ApiException("방에 멤버가 없어 시작할 수 없습니다.", HttpStatus.BAD_REQUEST);
        }
        if (room.getCapacity() > 1 && memberCount < 2) {
            throw new ApiException("단체 매칭은 2명 이상 모여야 시작할 수 있습니다.", HttpStatus.BAD_REQUEST);
        }

        if (!"started".equals(room.getStatus())) {
            room.setStatus("started");
            matchingRoomRepository.save(room);

            String teamIdStr = "team-" + room.getId();
            List<MatchingRoomMember> members = matchingRoomMemberRepository.findByMatchingRoomIdWithUser(room.getId());
            for (MatchingRoomMember m : members) {
                User joinedUser = m.getUser();
                if (joinedUser != null) {
                    joinedUser.setTeamId(teamIdStr);
                    userRepository.save(joinedUser);
                }
            }
        }

        return Map.of(
                "success", true,
                "message", "Started",
                "teamId", "team-" + room.getId(),
                "competitionId", 1,
                "detail", getRoomDetail(toApiId(room.getId()), user)
        );
    }

    public Map<String, Object> getRoomDetail(String roomId, User currentUser) {
        MatchingRoom room = findRoomByApiIdFlexible(roomId);
        List<MatchingRoomMember> joinedMembers = matchingRoomMemberRepository.findByMatchingRoomIdWithUser(room.getId());
        List<Long> invitedUserIds = pendingInviteUserIdsByRoomId.getOrDefault(room.getId(), List.of());

        Map<String, Object> body = new HashMap<>();
        body.put("roomId", toApiId(room.getId()));
        body.put("name", room.getName());
        body.put("status", resolveMatchingStatus(room, joinedMembers, invitedUserIds));
        body.put("marketType", room.getMarketType() != null ? room.getMarketType() : "KR");
        body.put("marketLabel", marketLabel(room.getMarketType()));
        body.put("marketDescription", marketDescription(room.getMarketType()));
        body.put("matchType", room.getMatchType() != null ? room.getMatchType() : "RANDOM");
        body.put("matchLabel", matchLabel(room.getMatchType()));
        body.put("matchDescription", matchDescription(room.getMatchType()));
        body.put("statusTitle", resolveStatusTitle(room, joinedMembers, invitedUserIds));
        body.put("statusDescription", resolveStatusDescription(room, joinedMembers, invitedUserIds));
        body.put("progress", buildProgress(room, joinedMembers, invitedUserIds));
        body.put("inviteCode", room.getInviteCode());
        body.put("memberCount", joinedMembers.size());
        body.put("capacity", room.getCapacity());
        body.put("members", buildMemberCards(room, currentUser, joinedMembers, invitedUserIds));
        body.put("actions", Map.of(
                "shareEnabled", "FRIEND".equalsIgnoreCase(room.getMatchType()),
                "directInviteEnabled", "FRIEND".equalsIgnoreCase(room.getMatchType()),
                "randomMatchingEnabled", "RANDOM".equalsIgnoreCase(room.getMatchType()),
                "chatEnabled", "started".equalsIgnoreCase(room.getStatus()) || joinedMembers.size() >= room.getCapacity(),
                "startEnabled", joinedMembers.size() >= Math.min(room.getCapacity(), 2)
        ));
        return body;
    }

    @Transactional
    public Map<String, Object> deleteRoomByAdmin(String roomId) {
        MatchingRoom room = findRoomByApiIdFlexible(roomId);
        matchingRoomMemberRepository.deleteByMatchingRoom_Id(room.getId());
        pendingInviteUserIdsByRoomId.remove(room.getId());
        matchingRoomRepository.delete(room);
        return Map.of("success", true, "message", "방이 삭제되었습니다.");
    }

    @Transactional
    public Map<String, Object> removeMemberByAdmin(String roomId, Long userId) {
        MatchingRoom room = findRoomByApiIdFlexible(roomId);
        if (!matchingRoomMemberRepository.existsByMatchingRoomIdAndUserId(room.getId(), userId)) {
            throw new ApiException("해당 방에 속한 멤버가 아닙니다.", HttpStatus.NOT_FOUND);
        }

        matchingRoomMemberRepository.deleteByMatchingRoomIdAndUserId(room.getId(), userId);
        User kickedUser = userRepository.findById(userId).orElse(null);
        if (kickedUser != null) {
            kickedUser.setTeamId(null);
            userRepository.saveAndFlush(kickedUser);
        }

        int newCount = (int) matchingRoomMemberRepository.countByMatchingRoomId(room.getId());
        room.setMemberCount(newCount);
        matchingRoomRepository.save(room);
        if (newCount == 0) {
            pendingInviteUserIdsByRoomId.remove(room.getId());
            matchingRoomRepository.delete(room);
        }
        return Map.of("success", true, "message", "멤버가 방에서 제거되었습니다.");
    }

    private Map<String, Object> doJoin(MatchingRoom room, User user) {
        if (matchingRoomMemberRepository.existsByMatchingRoomIdAndUserId(room.getId(), user.getId())) {
            throw new ApiException("이미 참여 중인 방입니다.", HttpStatus.BAD_REQUEST);
        }

        long currentCount = matchingRoomMemberRepository.countByMatchingRoomId(room.getId());
        if (currentCount >= room.getCapacity()) {
            throw new ApiException("방이 가득 찼습니다.", HttpStatus.BAD_REQUEST);
        }

        matchingRoomMemberRepository.save(MatchingRoomMember.of(room, user));
        room.setMemberCount((int) matchingRoomMemberRepository.countByMatchingRoomId(room.getId()));
        matchingRoomRepository.save(room);
        pendingInviteUserIdsByRoomId.computeIfPresent(room.getId(), (id, invitedIds) ->
                invitedIds.stream()
                        .filter(invitedUserId -> !invitedUserId.equals(user.getId()))
                        .collect(Collectors.toList()));

        return Map.of(
                "success", true,
                "message", "Joined",
                "room", Map.of("id", toApiId(room.getId()), "memberCount", room.getMemberCount()),
                "detail", getRoomDetail(toApiId(room.getId()), user)
        );
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
        throw new ApiException("초대 코드 생성에 실패했습니다. 잠시 후 다시 시도해 주세요.", HttpStatus.INTERNAL_SERVER_ERROR);
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
        String value = roomId.trim();
        if (value.startsWith(ROOM_ID_PREFIX)) {
            try {
                return Long.parseLong(value.substring(ROOM_ID_PREFIX.length()));
            } catch (NumberFormatException e) {
                throw new ApiException("잘못된 방 ID입니다.", HttpStatus.BAD_REQUEST);
            }
        }
        try {
            return Long.parseLong(value);
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
                    User user = m.getUser();
                    String uid = user.getId() != null ? user.getId().toString() : "";
                    return Map.<String, Object>of(
                            "id", uid,
                            "userId", uid,
                            "nickname", user.getNickname() != null ? user.getNickname() : "",
                            "level", 15,
                            "investmentProfileLabel", user.getInvestmentProfileResult() != null && !user.getInvestmentProfileResult().isBlank()
                                    ? user.getInvestmentProfileResult()
                                    : "균형잡힌 판다형"
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
        map.put("matchType", room.getMatchType() != null ? room.getMatchType() : "RANDOM");
        map.put("marketType", room.getMarketType() != null ? room.getMarketType() : "KR");
        map.put("inviteCode", room.getInviteCode());
        map.put("createdAt", room.getCreatedAt().toString());
        return map;
    }

    private Map<String, Object> toMapWithJoined(MatchingRoom room, boolean isJoined) {
        Map<String, Object> map = new HashMap<>(toMap(room));
        map.put("isJoined", isJoined);
        return map;
    }

    private Map<String, Object> buildProgress(MatchingRoom room,
                                              List<MatchingRoomMember> joinedMembers,
                                              List<Long> invitedUserIds) {
        int current = 1;
        if ("FRIEND".equalsIgnoreCase(room.getMatchType()) && !invitedUserIds.isEmpty()) {
            current = 2;
        }
        if ("started".equalsIgnoreCase(room.getStatus()) || joinedMembers.size() >= room.getCapacity()) {
            current = 3;
        }
        return Map.of("current", current, "total", 3);
    }

    private List<Map<String, Object>> buildMemberCards(MatchingRoom room,
                                                       User currentUser,
                                                       List<MatchingRoomMember> joinedMembers,
                                                       List<Long> invitedUserIds) {
        List<Map<String, Object>> members = new ArrayList<>();
        for (int i = 0; i < joinedMembers.size(); i++) {
            MatchingRoomMember member = joinedMembers.get(i);
            User user = member.getUser();
            boolean isMe = currentUser != null && currentUser.getId() != null && currentUser.getId().equals(user.getId());
            members.add(Map.of(
                    "userId", user.getId(),
                    "nickname", user.getNickname() != null ? user.getNickname() : "",
                    "level", 15,
                    "investmentProfileLabel", user.getInvestmentProfileResult() != null && !user.getInvestmentProfileResult().isBlank()
                            ? user.getInvestmentProfileResult()
                            : "균형잡힌 판다형",
                    "profileImageUrl", user.getProfileImageUrl() != null ? user.getProfileImageUrl() : "",
                    "role", i == 0 ? "HOST" : "MEMBER",
                    "roleLabel", i == 0 ? "방장" : "팀원",
                    "isMe", isMe,
                    "status", "CONFIRMED"
            ));
        }

        for (Long invitedUserId : invitedUserIds) {
            userRepository.findById(invitedUserId).ifPresent(invitedUser -> members.add(Map.of(
                    "userId", invitedUser.getId(),
                    "nickname", invitedUser.getNickname() != null ? invitedUser.getNickname() : "",
                    "level", 15,
                    "investmentProfileLabel", invitedUser.getInvestmentProfileResult() != null && !invitedUser.getInvestmentProfileResult().isBlank()
                            ? invitedUser.getInvestmentProfileResult()
                            : "균형잡힌 판다형",
                    "profileImageUrl", invitedUser.getProfileImageUrl() != null ? invitedUser.getProfileImageUrl() : "",
                    "role", "INVITED",
                    "roleLabel", "초대 완료",
                    "isMe", false,
                    "status", "INVITED"
            )));
        }

        int remaining = Math.max(0, room.getCapacity() - members.size());
        for (int i = 0; i < remaining; i++) {
            boolean randomMatch = "RANDOM".equalsIgnoreCase(room.getMatchType());
            members.add(Map.of(
                    "userId", "",
                    "nickname", randomMatch ? "지금 찾고 있어요" : "아직 비어 있어요",
                    "level", 15,
                    "investmentProfileLabel", randomMatch ? "관심사 기반 주식" : "친구를 직접 초대해 주세요",
                    "profileImageUrl", "",
                    "role", "EMPTY",
                    "roleLabel", randomMatch ? "매칭 중" : "초대 대기",
                    "isMe", false,
                    "status", randomMatch ? "SEARCHING" : "EMPTY"
            ));
        }
        return members;
    }

    private String resolveMatchingStatus(MatchingRoom room,
                                         List<MatchingRoomMember> joinedMembers,
                                         List<Long> invitedUserIds) {
        if ("started".equalsIgnoreCase(room.getStatus()) || joinedMembers.size() >= room.getCapacity()) {
            return "COMPLETED";
        }
        if ("RANDOM".equalsIgnoreCase(room.getMatchType())) {
            return "MATCHING";
        }
        if (!invitedUserIds.isEmpty()) {
            return "INVITING";
        }
        return "WAITING";
    }

    private String resolveStatusTitle(MatchingRoom room,
                                      List<MatchingRoomMember> joinedMembers,
                                      List<Long> invitedUserIds) {
        return switch (resolveMatchingStatus(room, joinedMembers, invitedUserIds)) {
            case "MATCHING" -> "최적의 파트너를 찾고 있어요";
            case "INVITING" -> "함께할 친구를 초대해 주세요";
            case "COMPLETED" -> "매칭을 완료했어요!";
            default -> "원하는 매칭 방법을 선택해 주세요";
        };
    }

    private String resolveStatusDescription(MatchingRoom room,
                                            List<MatchingRoomMember> joinedMembers,
                                            List<Long> invitedUserIds) {
        return switch (resolveMatchingStatus(room, joinedMembers, invitedUserIds)) {
            case "MATCHING" -> "비슷한 투자 성향의 파트너를 찾고 있어요";
            case "INVITING" -> "친구를 선택하거나 카카오톡으로 초대해 보세요";
            case "COMPLETED" -> "채팅방에 입장해 함께 투자를 시작할 수 있어요";
            default -> matchDescription(room.getMatchType());
        };
    }

    private static String normalizeVisibility(String visibility) {
        String resolved = visibility != null ? visibility.trim().toUpperCase() : VISIBILITY_PUBLIC;
        return (VISIBILITY_PUBLIC.equals(resolved) || VISIBILITY_PRIVATE.equals(resolved)) ? resolved : VISIBILITY_PUBLIC;
    }

    private static String normalizeMatchType(String matchType) {
        String resolved = matchType != null ? matchType.trim().toUpperCase() : "RANDOM";
        return ("FRIEND".equals(resolved) || "RANDOM".equals(resolved)) ? resolved : "RANDOM";
    }

    private static String normalizeMarketType(String marketType) {
        String resolved = marketType != null ? marketType.trim().toUpperCase() : "KR";
        return ("KR".equals(resolved) || "US".equals(resolved)) ? resolved : "KR";
    }

    private static String marketLabel(String marketType) {
        return "US".equalsIgnoreCase(marketType) ? "해외 주식" : "국내 주식";
    }

    private static String marketDescription(String marketType) {
        return "US".equalsIgnoreCase(marketType) ? "NASDAQ / S&P500" : "KOSPI / KOSDAQ";
    }

    private static String matchLabel(String matchType) {
        return "FRIEND".equalsIgnoreCase(matchType) ? "친구 초대" : "랜덤 매칭";
    }

    private static String matchDescription(String matchType) {
        return "FRIEND".equalsIgnoreCase(matchType) ? "친구와 함께 수익률 경쟁!" : "비슷한 실력의 투자자와 대결";
    }

    private static String toApiId(Long id) {
        return id != null ? ROOM_ID_PREFIX + id : null;
    }
}

package com.uniport.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.uniport.dto.InvestmentTestReservationRequestDTO;
import com.uniport.dto.InvestmentTestReservationResponseDTO;
import com.uniport.entity.InvestmentTestReservation;
import com.uniport.exception.ApiException;
import com.uniport.repository.InvestmentTestReservationRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;

@Service
public class InvestmentTestReservationService {

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final Set<String> VALID_RESULT_KEYS = Set.of(
            "turtle",
            "cheetah",
            "owl",
            "panda",
            "dolphin",
            "researcher",
            "farmer",
            "surfer"
    );

    private final InvestmentTestReservationRepository repository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public InvestmentTestReservationService(InvestmentTestReservationRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public InvestmentTestReservationResponseDTO submit(
            InvestmentTestReservationRequestDTO request,
            String userAgent
    ) {
        if (request == null || !Boolean.TRUE.equals(request.getConsent())) {
            throw new ApiException("개인정보 수집 및 이용 동의가 필요합니다.", HttpStatus.BAD_REQUEST);
        }

        String name = normalizeName(request.getName());
        Contact contact = normalizeContact(request.getContact());
        String resultKey = normalizeResultKey(request.getResultKey());
        String resultTitle = requireText(request.getResultTitle(), "resultTitle is required");
        String interestKeywordsJson = writeJson(request.getInterestKeywords() == null ? List.of() : request.getInterestKeywords());
        String answersJson = writeJson(request.getAnswers() == null ? Map.of() : new TreeMap<>(request.getAnswers()));

        InvestmentTestReservation reservation = repository
                .findByContactTypeAndContactValue(contact.type(), contact.value())
                .orElseGet(() -> InvestmentTestReservation.builder()
                        .contactType(contact.type())
                        .contactValue(contact.value())
                        .build());
        reservation.setName(name);
        reservation.setConsent(true);
        reservation.setResultKey(resultKey);
        reservation.setResultTitle(resultTitle);
        reservation.setInterestKeywordsJson(interestKeywordsJson);
        reservation.setAnswersJson(answersJson);
        reservation.setUserAgent(normalizeUserAgent(userAgent));

        InvestmentTestReservation saved = repository.save(reservation);
        return toResponse(saved);
    }

    private InvestmentTestReservationResponseDTO toResponse(InvestmentTestReservation reservation) {
        return InvestmentTestReservationResponseDTO.builder()
                .id(reservation.getId())
                .name(reservation.getName())
                .contactType(reservation.getContactType())
                .contactValue(reservation.getContactValue())
                .resultKey(reservation.getResultKey())
                .resultTitle(reservation.getResultTitle())
                .message("투자성향 테스트 사전예약이 저장됐습니다.")
                .build();
    }

    private String normalizeName(String value) {
        String name = requireText(value, "name is required");
        if (name.codePointCount(0, name.length()) > 10) {
            throw new ApiException("name must be 10 characters or fewer", HttpStatus.BAD_REQUEST);
        }
        return name;
    }

    private Contact normalizeContact(String value) {
        String email = requireText(value, "email is required").toLowerCase(Locale.ROOT);
        if (EMAIL_PATTERN.matcher(email).matches()) {
            return new Contact("EMAIL", email);
        }
        throw new ApiException("valid email is required", HttpStatus.BAD_REQUEST);
    }

    private String normalizeResultKey(String value) {
        String resultKey = requireText(value, "resultKey is required");
        if (!VALID_RESULT_KEYS.contains(resultKey)) {
            throw new ApiException("valid resultKey is required", HttpStatus.BAD_REQUEST);
        }
        return resultKey;
    }

    private String normalizeUserAgent(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String userAgent = value.trim();
        if (userAgent.length() <= 1000) {
            return userAgent;
        }
        return userAgent.substring(0, 1000);
    }

    private String requireText(String value, String message) {
        String trimmed = value == null ? "" : value.trim();
        if (trimmed.isBlank()) {
            throw new ApiException(message, HttpStatus.BAD_REQUEST);
        }
        return trimmed;
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new ApiException("request payload cannot be serialized", HttpStatus.BAD_REQUEST);
        }
    }

    private record Contact(String type, String value) {
    }
}

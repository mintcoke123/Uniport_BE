package com.uniport.service;

import com.uniport.dto.InvestmentTestReservationRequestDTO;
import com.uniport.dto.InvestmentTestReservationResponseDTO;
import com.uniport.entity.InvestmentTestReservation;
import com.uniport.exception.ApiException;
import com.uniport.repository.InvestmentTestReservationRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InvestmentTestReservationServiceTest {

    @Test
    void submitStoresEmailReservationWithNormalizedContactAndJsonPayloads() {
        InvestmentTestReservationRepository repository = mock(InvestmentTestReservationRepository.class);
        when(repository.findByContactTypeAndContactValue("EMAIL", "kim@example.com")).thenReturn(Optional.empty());
        when(repository.save(any(InvestmentTestReservation.class))).thenAnswer(invocation -> {
            InvestmentTestReservation reservation = invocation.getArgument(0);
            reservation.setId(12L);
            return reservation;
        });
        InvestmentTestReservationService service = new InvestmentTestReservationService(repository);

        InvestmentTestReservationResponseDTO response = service.submit(InvestmentTestReservationRequestDTO.builder()
                .name(" 김유니 ")
                .contact("KIM@EXAMPLE.COM")
                .consent(true)
                .resultKey("turtle")
                .resultTitle("조심스러운 거북이형")
                .interestKeywords(List.of("AI 반도체", "배당주"))
                .answers(Map.of("risk", "hold", "goal", "steady"))
                .build(), "Mozilla/5.0");

        ArgumentCaptor<InvestmentTestReservation> captor = ArgumentCaptor.forClass(InvestmentTestReservation.class);
        verify(repository).save(captor.capture());
        InvestmentTestReservation saved = captor.getValue();
        assertEquals("김유니", saved.getName());
        assertEquals("EMAIL", saved.getContactType());
        assertEquals("kim@example.com", saved.getContactValue());
        assertEquals("turtle", saved.getResultKey());
        assertEquals("조심스러운 거북이형", saved.getResultTitle());
        assertEquals("[\"AI 반도체\",\"배당주\"]", saved.getInterestKeywordsJson());
        assertEquals("{\"goal\":\"steady\",\"risk\":\"hold\"}", saved.getAnswersJson());
        assertEquals("Mozilla/5.0", saved.getUserAgent());
        assertEquals(12L, response.getId());
        assertEquals("EMAIL", response.getContactType());
        assertEquals("kim@example.com", response.getContactValue());
    }

    @Test
    void submitNormalizesEmailContactToLowercase() {
        InvestmentTestReservationRepository repository = mock(InvestmentTestReservationRepository.class);
        when(repository.findByContactTypeAndContactValue("EMAIL", "user@example.com")).thenReturn(Optional.empty());
        when(repository.save(any(InvestmentTestReservation.class))).thenAnswer(invocation -> invocation.getArgument(0));
        InvestmentTestReservationService service = new InvestmentTestReservationService(repository);

        service.submit(InvestmentTestReservationRequestDTO.builder()
                .name("김유니")
                .contact(" USER@EXAMPLE.COM ")
                .consent(true)
                .resultKey("panda")
                .resultTitle("느긋한 판다형")
                .build(), null);

        ArgumentCaptor<InvestmentTestReservation> captor = ArgumentCaptor.forClass(InvestmentTestReservation.class);
        verify(repository).save(captor.capture());
        assertEquals("EMAIL", captor.getValue().getContactType());
        assertEquals("user@example.com", captor.getValue().getContactValue());
    }

    @Test
    void submitAcceptsOwlResultKeyFromCurrentFigmaProfiles() {
        InvestmentTestReservationRepository repository = mock(InvestmentTestReservationRepository.class);
        when(repository.findByContactTypeAndContactValue("EMAIL", "owl@example.com")).thenReturn(Optional.empty());
        when(repository.save(any(InvestmentTestReservation.class))).thenAnswer(invocation -> invocation.getArgument(0));
        InvestmentTestReservationService service = new InvestmentTestReservationService(repository);

        service.submit(InvestmentTestReservationRequestDTO.builder()
                .name("김유니")
                .contact("owl@example.com")
                .consent(true)
                .resultKey("owl")
                .resultTitle("전략짜는 올빼미형")
                .build(), null);

        ArgumentCaptor<InvestmentTestReservation> captor = ArgumentCaptor.forClass(InvestmentTestReservation.class);
        verify(repository).save(captor.capture());
        assertEquals("owl", captor.getValue().getResultKey());
        assertEquals("전략짜는 올빼미형", captor.getValue().getResultTitle());
    }

    @Test
    void submitRejectsMissingConsent() {
        InvestmentTestReservationService service = new InvestmentTestReservationService(
                mock(InvestmentTestReservationRepository.class)
        );

        ApiException exception = assertThrows(ApiException.class, () -> service.submit(InvestmentTestReservationRequestDTO.builder()
                .name("김유니")
                .contact("user@example.com")
                .consent(false)
                .resultKey("turtle")
                .resultTitle("조심스러운 거북이형")
                .build(), null));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatus());
    }

    @Test
    void submitRejectsInvalidContact() {
        InvestmentTestReservationService service = new InvestmentTestReservationService(
                mock(InvestmentTestReservationRepository.class)
        );

        ApiException exception = assertThrows(ApiException.class, () -> service.submit(InvestmentTestReservationRequestDTO.builder()
                .name("김유니")
                .contact("not-a-contact")
                .consent(true)
                .resultKey("turtle")
                .resultTitle("조심스러운 거북이형")
                .build(), null));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatus());
    }

    @Test
    void submitRejectsPhoneContact() {
        InvestmentTestReservationService service = new InvestmentTestReservationService(
                mock(InvestmentTestReservationRepository.class)
        );

        ApiException exception = assertThrows(ApiException.class, () -> service.submit(InvestmentTestReservationRequestDTO.builder()
                .name("김유니")
                .contact("01012345678")
                .consent(true)
                .resultKey("turtle")
                .resultTitle("조심스러운 거북이형")
                .build(), null));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatus());
    }

    @Test
    void submitRejectsNameLongerThanTenCodePoints() {
        InvestmentTestReservationService service = new InvestmentTestReservationService(
                mock(InvestmentTestReservationRepository.class)
        );

        ApiException exception = assertThrows(ApiException.class, () -> service.submit(InvestmentTestReservationRequestDTO.builder()
                .name("가나다라마바사아자차카")
                .contact("user@example.com")
                .consent(true)
                .resultKey("turtle")
                .resultTitle("조심스러운 거북이형")
                .build(), null));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatus());
    }

    @Test
    void submitUpdatesExistingReservationForDuplicateContact() {
        InvestmentTestReservation existing = InvestmentTestReservation.builder()
                .id(5L)
                .name("이전")
                .contactType("EMAIL")
                .contactValue("existing@example.com")
                .consent(true)
                .resultKey("turtle")
                .resultTitle("이전 결과")
                .interestKeywordsJson("[]")
                .answersJson("{}")
                .build();
        InvestmentTestReservationRepository repository = mock(InvestmentTestReservationRepository.class);
        when(repository.findByContactTypeAndContactValue("EMAIL", "existing@example.com")).thenReturn(Optional.of(existing));
        when(repository.save(any(InvestmentTestReservation.class))).thenAnswer(invocation -> invocation.getArgument(0));
        InvestmentTestReservationService service = new InvestmentTestReservationService(repository);

        InvestmentTestReservationResponseDTO response = service.submit(InvestmentTestReservationRequestDTO.builder()
                .name("새이름")
                .contact("EXISTING@EXAMPLE.COM")
                .consent(true)
                .resultKey("surfer")
                .resultTitle("파도타는 서퍼형")
                .interestKeywords(List.of("ETF"))
                .answers(Map.of("style", "active"))
                .build(), "Updated UA");

        ArgumentCaptor<InvestmentTestReservation> captor = ArgumentCaptor.forClass(InvestmentTestReservation.class);
        verify(repository).save(captor.capture());
        assertEquals(5L, captor.getValue().getId());
        assertEquals("새이름", captor.getValue().getName());
        assertEquals("EMAIL", captor.getValue().getContactType());
        assertEquals("existing@example.com", captor.getValue().getContactValue());
        assertEquals("surfer", captor.getValue().getResultKey());
        assertEquals("[\"ETF\"]", captor.getValue().getInterestKeywordsJson());
        assertEquals("Updated UA", captor.getValue().getUserAgent());
        assertEquals(5L, response.getId());
    }
}

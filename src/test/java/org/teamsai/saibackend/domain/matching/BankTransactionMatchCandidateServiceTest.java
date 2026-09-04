package org.teamsai.saibackend.domain.matching;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.teamsai.saibackend.domain.matching.dto.BankTransactionMatchCandidateDTO;
import org.teamsai.saibackend.domain.matching.dto.BankTransactionMatchCandidateQueryDTO;
import org.teamsai.saibackend.domain.matching.exception.MatchingErrorCode;
import org.teamsai.saibackend.domain.matching.mapper.BankTransactionMatchCandidateMapper;
import org.teamsai.saibackend.domain.matching.service.EvaluatedMatchingCandidate;
import org.teamsai.saibackend.domain.matching.service.MatchingCandidate;
import org.teamsai.saibackend.domain.matching.service.BankTransactionMatchCandidateService;
import org.teamsai.saibackend.domain.matching.type.MatchingAmountType;
import org.teamsai.saibackend.domain.matching.type.MatchingCandidateInvalidationReason;
import org.teamsai.saibackend.domain.matching.type.MatchingCandidateStatus;
import org.teamsai.saibackend.domain.matching.type.MatchingTargetType;
import org.teamsai.saibackend.global.exception.DomainException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BankTransactionMatchCandidateServiceTest {

    @Mock
    private BankTransactionMatchCandidateMapper candidateMapper;

    private BankTransactionMatchCandidateService candidateService;

    @BeforeEach
    void setUp() {
        candidateService = new BankTransactionMatchCandidateService(
                candidateMapper
        );
    }

    @Test
    void savesEvaluatedCandidatesAsDtos() {
        Long bankTransactionId = 100L;
        EvaluatedMatchingCandidate settlementCandidate =
                evaluatedCandidate(
                        MatchingTargetType.SETTLEMENT,
                        10L,
                        "10000",
                        MatchingAmountType.PARTIAL
                );
        EvaluatedMatchingCandidate loanCandidate =
                evaluatedCandidate(
                        MatchingTargetType.LOAN,
                        20L,
                        "20000",
                        MatchingAmountType.EXACT
                );

        when(candidateMapper.insertAll(org.mockito.ArgumentMatchers.anyList()))
                .thenReturn(2);

        candidateService.saveAll(
                bankTransactionId,
                List.of(settlementCandidate, loanCandidate)
        );

        ArgumentCaptor<List<BankTransactionMatchCandidateDTO>> captor =
                ArgumentCaptor.forClass(List.class);
        verify(candidateMapper).insertAll(captor.capture());

        List<BankTransactionMatchCandidateDTO> savedCandidates =
                captor.getValue();

        assertThat(savedCandidates).hasSize(2);
        assertThat(savedCandidates)
                .extracting(
                        BankTransactionMatchCandidateDTO::getBankTransactionId,
                        BankTransactionMatchCandidateDTO::getTargetType,
                        BankTransactionMatchCandidateDTO::getTargetId,
                        BankTransactionMatchCandidateDTO::getExpectedRemainingAmount,
                        BankTransactionMatchCandidateDTO::getAmountMatchType,
                        BankTransactionMatchCandidateDTO::getCandidateStatus
                )
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(
                                100L,
                                MatchingTargetType.SETTLEMENT,
                                10L,
                                new BigDecimal("10000"),
                                MatchingAmountType.PARTIAL,
                                MatchingCandidateStatus.AVAILABLE
                        ),
                        org.assertj.core.groups.Tuple.tuple(
                                100L,
                                MatchingTargetType.LOAN,
                                20L,
                                new BigDecimal("20000"),
                                MatchingAmountType.EXACT,
                                MatchingCandidateStatus.AVAILABLE
                        )
                );
        assertThat(savedCandidates)
                .allSatisfy(candidate -> {
                    assertThat(candidate.getInvalidatedAt()).isNull();
                    assertThat(candidate.getInvalidationReason()).isNull();
                });
        assertThat(savedCandidates)
                .extracting(BankTransactionMatchCandidateDTO::getCreatedAt)
                .doesNotContainNull()
                .allMatch(createdAt -> createdAt.equals(
                        savedCandidates.get(0).getCreatedAt()
                ));
    }

    @Test
    void doesNotCallMapperWhenCandidatesAreEmpty() {
        candidateService.saveAll(100L, List.of());

        verify(candidateMapper, never()).insertAll(
                org.mockito.ArgumentMatchers.anyList()
        );
    }

    @Test
    void failsWhenInsertedCountDoesNotMatchCandidateCount() {
        EvaluatedMatchingCandidate candidate = evaluatedCandidate(
                MatchingTargetType.SETTLEMENT,
                10L,
                "10000",
                MatchingAmountType.EXACT
        );

        when(candidateMapper.insertAll(org.mockito.ArgumentMatchers.anyList()))
                .thenReturn(0);

        assertThatThrownBy(() -> candidateService.saveAll(
                100L,
                List.of(candidate)
        )).isInstanceOfSatisfying(
                DomainException.class,
                exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(
                                MatchingErrorCode
                                        .MATCHING_CANDIDATE_SAVE_FAILED
                        )
        );
    }

    @Test
    void failsWhenSaveRequestContainsNullCandidate() {
        assertThatThrownBy(() -> candidateService.saveAll(
                100L,
                java.util.Arrays.asList(
                        (EvaluatedMatchingCandidate) null
                )
        )).isInstanceOfSatisfying(
                DomainException.class,
                exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(MatchingErrorCode.INVALID_MATCHING_REQUEST)
        );
    }

    @Test
    void returnsCandidatesForBankTransaction() {
        BankTransactionMatchCandidateDTO candidate = dto(1L, 100L);

        when(candidateMapper.findAllByBankTransactionId(100L))
                .thenReturn(List.of(candidate));

        List<BankTransactionMatchCandidateDTO> result =
                candidateService.findAllByBankTransactionId(100L);

        assertThat(result).containsExactly(candidate);
    }

    @Test
    void returnsCandidatesWithParticipantNameForReview() {
        BankTransactionMatchCandidateQueryDTO candidate =
                BankTransactionMatchCandidateQueryDTO.builder()
                        .matchCandidateId(1L)
                        .bankTransactionId(100L)
                        .targetType(MatchingTargetType.SETTLEMENT)
                        .targetId(10L)
                        .participantName("홍길동")
                        .expectedRemainingAmount(
                                new BigDecimal("10000")
                        )
                        .amountMatchType(MatchingAmountType.EXACT)
                        .createdAt(LocalDateTime.now())
                        .build();

        when(candidateMapper.findAllForReviewByBankTransactionId(100L))
                .thenReturn(List.of(candidate));

        List<BankTransactionMatchCandidateQueryDTO> result =
                candidateService.findAllForReviewByBankTransactionId(100L);

        assertThat(result).containsExactly(candidate);
    }

    @Test
    void returnsCandidatesForTransactionPage() {
        BankTransactionMatchCandidateQueryDTO candidate =
                BankTransactionMatchCandidateQueryDTO.builder()
                        .matchCandidateId(1L)
                        .bankTransactionId(100L)
                        .targetType(MatchingTargetType.SETTLEMENT)
                        .targetId(10L)
                        .aggregateId(20L)
                        .build();

        when(candidateMapper.findAllForReviewByBankTransactionIds(
                List.of(100L, 101L),
                MatchingTargetType.SETTLEMENT,
                20L
        )).thenReturn(List.of(candidate));

        List<BankTransactionMatchCandidateQueryDTO> result =
                candidateService.findAllForReviewByBankTransactionIds(
                        List.of(100L, 101L),
                        MatchingTargetType.SETTLEMENT,
                        20L
                );

        assertThat(result).containsExactly(candidate);
    }

    @Test
    void rejectsEmptyTransactionPage() {
        assertThatThrownBy(() ->
                candidateService.findAllForReviewByBankTransactionIds(
                        List.of(),
                        null,
                        null
                )
        ).isInstanceOfSatisfying(
                DomainException.class,
                exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(MatchingErrorCode.INVALID_MATCHING_REQUEST)
        );

        verify(candidateMapper, never())
                .findAllForReviewByBankTransactionIds(
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any()
                );
    }

    @Test
    void returnsCandidateBelongingToBankTransaction() {
        BankTransactionMatchCandidateDTO candidate = dto(1L, 100L);

        when(candidateMapper.findByIdAndBankTransactionId(1L, 100L))
                .thenReturn(Optional.of(candidate));

        BankTransactionMatchCandidateDTO result =
                candidateService.findByIdAndBankTransactionId(1L, 100L);

        assertThat(result).isSameAs(candidate);
    }

    @Test
    void failsWhenCandidateDoesNotBelongToBankTransaction() {
        when(candidateMapper.findByIdAndBankTransactionId(1L, 100L))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                candidateService.findByIdAndBankTransactionId(1L, 100L)
        ).isInstanceOfSatisfying(
                DomainException.class,
                exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(
                                MatchingErrorCode.MATCHING_CANDIDATE_NOT_FOUND
                        )
        );
    }

    @Test
    void invalidatesAvailableCandidate() {
        when(candidateMapper.invalidate(
                org.mockito.ArgumentMatchers.eq(1L),
                org.mockito.ArgumentMatchers.eq(100L),
                org.mockito.ArgumentMatchers.eq(
                        MatchingCandidateInvalidationReason
                                .TARGET_NOT_AVAILABLE
                ),
                org.mockito.ArgumentMatchers.any(LocalDateTime.class)
        )).thenReturn(1);

        candidateService.invalidateCandidate(
                1L,
                100L,
                MatchingCandidateInvalidationReason.TARGET_NOT_AVAILABLE
        );

        verify(candidateMapper).invalidate(
                org.mockito.ArgumentMatchers.eq(1L),
                org.mockito.ArgumentMatchers.eq(100L),
                org.mockito.ArgumentMatchers.eq(
                        MatchingCandidateInvalidationReason
                                .TARGET_NOT_AVAILABLE
                ),
                org.mockito.ArgumentMatchers.any(LocalDateTime.class)
        );
    }

    @Test
    void failsWhenCandidateCannotBeInvalidated() {
        when(candidateMapper.invalidate(
                org.mockito.ArgumentMatchers.eq(1L),
                org.mockito.ArgumentMatchers.eq(100L),
                org.mockito.ArgumentMatchers.eq(
                        MatchingCandidateInvalidationReason.TARGET_NOT_FOUND
                ),
                org.mockito.ArgumentMatchers.any(LocalDateTime.class)
        )).thenReturn(0);

        assertThatThrownBy(() -> candidateService.invalidateCandidate(
                1L,
                100L,
                MatchingCandidateInvalidationReason.TARGET_NOT_FOUND
        )).isInstanceOfSatisfying(
                DomainException.class,
                exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(
                                MatchingErrorCode
                                        .MATCHING_CANDIDATE_INVALIDATION_FAILED
                        )
        );
    }

    @Test
    void failsWhenInvalidationReasonIsNull() {
        assertThatThrownBy(() -> candidateService.invalidateCandidate(
                1L,
                100L,
                null
        )).isInstanceOfSatisfying(
                DomainException.class,
                exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(MatchingErrorCode.INVALID_MATCHING_REQUEST)
        );

        verify(candidateMapper, never()).invalidate(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()
        );
    }

    @Test
    void returnsAvailableCandidateCount() {
        when(candidateMapper.countAvailableByBankTransactionId(100L))
                .thenReturn(2);

        int result = candidateService.countAvailableCandidates(100L);

        assertThat(result).isEqualTo(2);
    }

    private EvaluatedMatchingCandidate evaluatedCandidate(
            MatchingTargetType targetType,
            Long targetId,
            String remainingAmount,
            MatchingAmountType amountMatchType
    ) {
        MatchingCandidate candidate = new MatchingCandidate(
                targetType,
                targetId,
                targetId,
                "HongGilDong",
                new BigDecimal(remainingAmount)
        );

        return new EvaluatedMatchingCandidate(candidate, amountMatchType);
    }

    private BankTransactionMatchCandidateDTO dto(
            Long matchCandidateId,
            Long bankTransactionId
    ) {
        return BankTransactionMatchCandidateDTO.builder()
                .matchCandidateId(matchCandidateId)
                .bankTransactionId(bankTransactionId)
                .targetType(MatchingTargetType.SETTLEMENT)
                .targetId(10L)
                .expectedRemainingAmount(new BigDecimal("10000"))
                .amountMatchType(MatchingAmountType.EXACT)
                .candidateStatus(MatchingCandidateStatus.AVAILABLE)
                .createdAt(LocalDateTime.now())
                .build();
    }
}

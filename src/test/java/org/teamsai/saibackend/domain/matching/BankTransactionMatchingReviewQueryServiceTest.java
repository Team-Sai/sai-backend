package org.teamsai.saibackend.domain.matching;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.teamsai.saibackend.domain.matching.dto.BankTransactionMatchCandidateQueryDTO;
import org.teamsai.saibackend.domain.matching.dto.response.BankTransactionMatchingReviewResponse;
import org.teamsai.saibackend.domain.matching.exception.MatchingErrorCode;
import org.teamsai.saibackend.domain.matching.service.MatchingReviewValidator;
import org.teamsai.saibackend.domain.matching.service.BankTransactionMatchCandidateService;
import org.teamsai.saibackend.domain.matching.service.BankTransactionMatchingReviewQueryService;
import org.teamsai.saibackend.domain.matching.type.MatchingAmountType;
import org.teamsai.saibackend.domain.matching.type.MatchingReviewChannel;
import org.teamsai.saibackend.domain.matching.type.MatchingTargetType;
import org.teamsai.saibackend.domain.transaction.dto.response.BankTransactionDetailResponse;
import org.teamsai.saibackend.domain.transaction.service.BankTransactionQueryService;
import org.teamsai.saibackend.domain.transaction.type.BankTransactionProcessingStatus;
import org.teamsai.saibackend.domain.transaction.type.BankTransactionType;
import org.teamsai.saibackend.global.exception.DomainException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("BankTransactionMatchingReviewQueryService 단위 테스트")
class BankTransactionMatchingReviewQueryServiceTest {

    private static final Long USER_ID = 1L;
    private static final Long LINKED_ACCOUNT_ID = 2L;
    private static final Long BANK_TRANSACTION_ID = 3L;

    @Mock
    private BankTransactionQueryService bankTransactionQueryService;

    @Mock
    private BankTransactionMatchCandidateService candidateService;

    private BankTransactionMatchingReviewQueryService reviewQueryService;

    @BeforeEach
    void setUp() {
        reviewQueryService = new BankTransactionMatchingReviewQueryService(
                bankTransactionQueryService,
                candidateService,
                new MatchingReviewValidator()
        );
    }

    @Test
    @DisplayName("한 도메인의 후보만 있으면 거래내역 검토 대상으로 반환한다")
    void returnsTransactionHistoryChannelForSingleDomainCandidates() {
        BankTransactionDetailResponse transaction = transaction();
        BankTransactionMatchCandidateQueryDTO candidate = candidate(
                10L,
                MatchingTargetType.SETTLEMENT,
                MatchingAmountType.PARTIAL
        );

        given(bankTransactionQueryService.getTransactionDetail(
                USER_ID,
                LINKED_ACCOUNT_ID,
                BANK_TRANSACTION_ID
        )).willReturn(transaction);
        given(candidateService.findAllForReviewByBankTransactionId(
                BANK_TRANSACTION_ID
        )).willReturn(List.of(candidate));

        BankTransactionMatchingReviewResponse response =
                reviewQueryService.getReview(
                        USER_ID,
                        LINKED_ACCOUNT_ID,
                        BANK_TRANSACTION_ID
                );

        assertThat(response.transaction()).isEqualTo(transaction);
        assertThat(response.reviewChannel())
                .isEqualTo(MatchingReviewChannel.TRANSACTION_HISTORY);
        assertThat(response.candidates()).hasSize(1);
        assertThat(response.candidates().get(0).matchCandidateId())
                .isEqualTo(10L);
        assertThat(response.candidates().get(0).aggregateId())
                .isEqualTo(100L);
        assertThat(response.candidates().get(0).targetName())
                .isEqualTo("8월 회식비 정산");
        assertThat(response.candidates().get(0).participantName())
                .isEqualTo("홍길동");
        assertThat(response.candidates().get(0).amountMatchType())
                .isEqualTo(MatchingAmountType.PARTIAL);
    }

    @Test
    @DisplayName("정산과 차용증 후보가 모두 있으면 알림 검토 대상으로 반환한다")
    void returnsNotificationChannelForCrossDomainCandidates() {
        given(bankTransactionQueryService.getTransactionDetail(
                USER_ID,
                LINKED_ACCOUNT_ID,
                BANK_TRANSACTION_ID
        )).willReturn(transaction());
        given(candidateService.findAllForReviewByBankTransactionId(
                BANK_TRANSACTION_ID
        )).willReturn(List.of(
                candidate(
                        10L,
                        MatchingTargetType.SETTLEMENT,
                        MatchingAmountType.EXACT
                ),
                candidate(
                        11L,
                        MatchingTargetType.LOAN,
                        MatchingAmountType.PARTIAL
                )
        ));

        BankTransactionMatchingReviewResponse response =
                reviewQueryService.getReview(
                        USER_ID,
                        LINKED_ACCOUNT_ID,
                        BANK_TRANSACTION_ID
                );

        assertThat(response.reviewChannel())
                .isEqualTo(MatchingReviewChannel.TRANSACTION_HISTORY);
        assertThat(response.candidates()).hasSize(2);
        verify(bankTransactionQueryService).getTransactionDetail(
                USER_ID,
                LINKED_ACCOUNT_ID,
                BANK_TRANSACTION_ID
        );
    }

    @Test
    @DisplayName("후보가 없어도 거래내역 검토 대상으로 반환한다")
    void returnsTransactionHistoryChannelWhenCandidatesAreEmpty() {
        given(bankTransactionQueryService.getTransactionDetail(
                USER_ID,
                LINKED_ACCOUNT_ID,
                BANK_TRANSACTION_ID
        )).willReturn(transaction());
        given(candidateService.findAllForReviewByBankTransactionId(
                BANK_TRANSACTION_ID
        )).willReturn(List.of());

        BankTransactionMatchingReviewResponse response =
                reviewQueryService.getReview(
                        USER_ID,
                        LINKED_ACCOUNT_ID,
                        BANK_TRANSACTION_ID
                );

        assertThat(response.reviewChannel())
                .isEqualTo(MatchingReviewChannel.TRANSACTION_HISTORY);
        assertThat(response.candidates()).isEmpty();
    }

    @Test
    @DisplayName("이미 처리된 거래의 과거 후보는 반환하지 않는다")
    void rejectsReviewForProcessedTransaction() {
        given(bankTransactionQueryService.getTransactionDetail(
                USER_ID,
                LINKED_ACCOUNT_ID,
                BANK_TRANSACTION_ID
        )).willReturn(transaction(
                BankTransactionProcessingStatus.APPLIED,
                BankTransactionType.DEPOSIT
        ));

        assertThatThrownBy(() -> reviewQueryService.getReview(
                USER_ID,
                LINKED_ACCOUNT_ID,
                BANK_TRANSACTION_ID
        )).isInstanceOfSatisfying(
                DomainException.class,
                exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(
                                MatchingErrorCode.MATCHING_REVIEW_NOT_REQUIRED
                        )
        );

        verify(candidateService, never())
                .findAllForReviewByBankTransactionId(BANK_TRANSACTION_ID);
    }

    @Test
    @DisplayName("출금 거래의 매칭 후보는 반환하지 않는다")
    void rejectsReviewForWithdrawalTransaction() {
        given(bankTransactionQueryService.getTransactionDetail(
                USER_ID,
                LINKED_ACCOUNT_ID,
                BANK_TRANSACTION_ID
        )).willReturn(transaction(
                BankTransactionProcessingStatus.NEEDS_CHECK,
                BankTransactionType.WITHDRAWAL
        ));

        assertThatThrownBy(() -> reviewQueryService.getReview(
                USER_ID,
                LINKED_ACCOUNT_ID,
                BANK_TRANSACTION_ID
        )).isInstanceOfSatisfying(
                DomainException.class,
                exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(
                                MatchingErrorCode.MATCHING_REVIEW_NOT_REQUIRED
                        )
        );

        verify(candidateService, never())
                .findAllForReviewByBankTransactionId(BANK_TRANSACTION_ID);
    }

    private BankTransactionDetailResponse transaction() {
        return transaction(
                BankTransactionProcessingStatus.NEEDS_CHECK,
                BankTransactionType.DEPOSIT
        );
    }

    private BankTransactionDetailResponse transaction(
            BankTransactionProcessingStatus processingStatus,
            BankTransactionType transactionType
    ) {
        return new BankTransactionDetailResponse(
                BANK_TRANSACTION_ID,
                LINKED_ACCOUNT_ID,
                new BigDecimal("5000.00"),
                transactionType,
                processingStatus,
                LocalDateTime.of(2026, 8, 15, 10, 0),
                "홍길동",
                null,
                LocalDateTime.of(2026, 8, 15, 10, 5)
        );
    }

    private BankTransactionMatchCandidateQueryDTO candidate(
            Long matchCandidateId,
            MatchingTargetType targetType,
            MatchingAmountType amountMatchType
    ) {
        return BankTransactionMatchCandidateQueryDTO.builder()
                .matchCandidateId(matchCandidateId)
                .bankTransactionId(BANK_TRANSACTION_ID)
                .targetType(targetType)
                .targetId(20L + matchCandidateId)
                .aggregateId(100L)
                .targetName("8월 회식비 정산")
                .participantName("홍길동")
                .expectedRemainingAmount(new BigDecimal("10000.00"))
                .amountMatchType(amountMatchType)
                .createdAt(LocalDateTime.of(2026, 8, 15, 10, 5))
                .build();
    }
}

package org.teamsai.saibackend.domain.matching;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.teamsai.saibackend.domain.matching.exception.MatchingErrorCode;
import org.teamsai.saibackend.domain.matching.model.AutoMatchingExecutionResult;
import org.teamsai.saibackend.domain.matching.model.AutoMatchingTransactionResult;
import org.teamsai.saibackend.domain.matching.model.MatchingCandidate;
import org.teamsai.saibackend.domain.matching.model.MatchingTransaction;
import org.teamsai.saibackend.domain.matching.service.AutoMatchingService;
import org.teamsai.saibackend.domain.matching.service.BankMatchingService;
import org.teamsai.saibackend.domain.matching.type.AutoMatchingProcessStatus;
import org.teamsai.saibackend.domain.matching.type.AutoMatchingTransactionType;
import org.teamsai.saibackend.domain.matching.type.MatchingTargetType;
import org.teamsai.saibackend.domain.payment.mapper.PaymentObligationMapper;
import org.teamsai.saibackend.domain.transaction.dto.BankTransactionDTO;
import org.teamsai.saibackend.domain.transaction.exception.BankTransactionErrorCode;
import org.teamsai.saibackend.domain.transaction.service.BankTransactionService;
import org.teamsai.saibackend.domain.transaction.type.BankTransactionProcessingStatus;
import org.teamsai.saibackend.domain.transaction.type.BankTransactionType;
import org.teamsai.saibackend.global.exception.DomainException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.groups.Tuple.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("BankMatchingService 단위 테스트")
class BankMatchingServiceTest {

    private static final Long LINKED_ACCOUNT_ID = 1L;

    @Mock
    private BankTransactionService bankTransactionService;

    @Mock
    private PaymentObligationMapper paymentObligationMapper;

    @Mock
    private AutoMatchingService autoMatchingService;

    @InjectMocks
    private BankMatchingService bankMatchingService;

    @Nested
    @DisplayName("연결 계좌 기준 자동매칭 실행")
    class Execute {

        @Test
        @DisplayName("처리 대기 입금 거래가 없으면 빈 결과를 반환한다")
        void returnsEmptyResultWhenPendingDepositsDoNotExist() {
            given(bankTransactionService
                    .findPendingDepositsByLinkedAccountId(LINKED_ACCOUNT_ID))
                    .willReturn(List.of());

            AutoMatchingExecutionResult result =
                    bankMatchingService.execute(LINKED_ACCOUNT_ID);

            assertThat(result.totalTransactionCount()).isZero();
            assertThat(result.transactionResults()).isEmpty();
            verify(paymentObligationMapper, never())
                    .findMatchCandidatesByLinkedAccountId(any(), any());
            verify(autoMatchingService, never()).execute(any(), any());
        }

        @Test
        @DisplayName("유효한 입금 거래와 정산 후보로 자동매칭을 실행한다")
        void executesAutoMatchingWithPendingDepositsAndCandidates() {
            BankTransactionDTO transaction = bankTransaction(
                    101L,
                    "Hong Gil Dong"
            );
            MatchingCandidate candidate = candidate();
            AutoMatchingExecutionResult matchingResult = executionResult(
                    result(101L, AutoMatchingProcessStatus.APPLIED)
            );

            given(bankTransactionService
                    .findPendingDepositsByLinkedAccountId(LINKED_ACCOUNT_ID))
                    .willReturn(List.of(transaction));
            given(paymentObligationMapper
                    .findMatchCandidatesByLinkedAccountId(
                            LINKED_ACCOUNT_ID,
                            transaction.getTransactionAt()
                    ))
                    .willReturn(List.of(candidate));
            given(autoMatchingService.execute(any(), any()))
                    .willReturn(matchingResult);

            AutoMatchingExecutionResult result =
                    bankMatchingService.execute(LINKED_ACCOUNT_ID);

            ArgumentCaptor<List<MatchingTransaction>> transactionsCaptor =
                    ArgumentCaptor.forClass(List.class);
            verify(autoMatchingService).execute(
                    transactionsCaptor.capture(),
                    org.mockito.ArgumentMatchers.eq(List.of(candidate))
            );

            assertThat(transactionsCaptor.getValue())
                    .extracting(
                            MatchingTransaction::transactionId,
                            MatchingTransaction::transactionType,
                            MatchingTransaction::amount,
                            MatchingTransaction::counterpartyName,
                            MatchingTransaction::transactionAt
                    )
                    .containsExactly(tuple(
                            101L,
                            AutoMatchingTransactionType.DEPOSIT,
                            new BigDecimal("10000.00"),
                            "Hong Gil Dong",
                            transaction.getTransactionAt()
                    ));
            assertThat(result.transactionResults())
                    .extracting(
                            AutoMatchingTransactionResult::transactionId,
                            AutoMatchingTransactionResult::processStatus
                    )
                    .containsExactly(tuple(
                            101L,
                            AutoMatchingProcessStatus.APPLIED
                    ));
            verify(bankTransactionService).updateStatus(
                    101L,
                    BankTransactionProcessingStatus.PENDING,
                    BankTransactionProcessingStatus.APPLIED
            );
        }

        @Test
        @DisplayName("입금자명이 없으면 자동매칭 없이 확인 필요로 처리한다")
        void classifiesBlankCounterpartyNameAsNeedsCheck() {
            BankTransactionDTO transaction = bankTransaction(101L, " ");

            given(bankTransactionService
                    .findPendingDepositsByLinkedAccountId(LINKED_ACCOUNT_ID))
                    .willReturn(List.of(transaction));

            AutoMatchingExecutionResult result =
                    bankMatchingService.execute(LINKED_ACCOUNT_ID);

            assertThat(result.totalTransactionCount()).isEqualTo(1);
            assertThat(result.needsCheckCount()).isEqualTo(1);
            assertThat(result.transactionResults())
                    .extracting(
                            AutoMatchingTransactionResult::transactionId,
                            AutoMatchingTransactionResult::processStatus
                    )
                    .containsExactly(tuple(
                            101L,
                            AutoMatchingProcessStatus.NEEDS_CHECK
                    ));
            verify(paymentObligationMapper, never())
                    .findMatchCandidatesByLinkedAccountId(any(), any());
            verify(autoMatchingService, never()).execute(any(), any());
            verify(bankTransactionService).updateStatus(
                    101L,
                    BankTransactionProcessingStatus.PENDING,
                    BankTransactionProcessingStatus.NEEDS_CHECK
            );
        }

        @Test
        @DisplayName("원본 거래 조회 순서대로 최종 결과를 재조합한다")
        void preservesOriginalTransactionOrder() {
            BankTransactionDTO first = bankTransaction(101L, "Hong Gil Dong");
            BankTransactionDTO second = bankTransaction(102L, null);
            BankTransactionDTO third = bankTransaction(103L, "Kim Chul Soo");

            given(bankTransactionService
                    .findPendingDepositsByLinkedAccountId(LINKED_ACCOUNT_ID))
                    .willReturn(List.of(first, second, third));
            given(paymentObligationMapper
                    .findMatchCandidatesByLinkedAccountId(any(), any()))
                    .willReturn(List.of(candidate()));
            given(autoMatchingService.execute(any(), any()))
                    .willReturn(
                            executionResult(
                                    result(
                                            101L,
                                            AutoMatchingProcessStatus.APPLIED
                                    )
                            ),
                            executionResult(
                                    result(
                                            103L,
                                            AutoMatchingProcessStatus.UNMATCHED
                                    )
                            )
                    );

            AutoMatchingExecutionResult result =
                    bankMatchingService.execute(LINKED_ACCOUNT_ID);

            assertThat(result.transactionResults())
                    .extracting(
                            AutoMatchingTransactionResult::transactionId,
                            AutoMatchingTransactionResult::processStatus
                    )
                    .containsExactly(
                            tuple(101L, AutoMatchingProcessStatus.APPLIED),
                            tuple(102L, AutoMatchingProcessStatus.NEEDS_CHECK),
                            tuple(103L, AutoMatchingProcessStatus.UNMATCHED)
                    );
            verify(bankTransactionService).updateStatus(
                    101L,
                    BankTransactionProcessingStatus.PENDING,
                    BankTransactionProcessingStatus.APPLIED
            );
            verify(bankTransactionService).updateStatus(
                    102L,
                    BankTransactionProcessingStatus.PENDING,
                    BankTransactionProcessingStatus.NEEDS_CHECK
            );
            verify(bankTransactionService).updateStatus(
                    103L,
                    BankTransactionProcessingStatus.PENDING,
                    BankTransactionProcessingStatus.UNMATCHED
            );
        }

        @Test
        @DisplayName("자동매칭 결과별 은행 거래 상태를 변경한다")
        void doesNotReuseCandidateAfterPreviousTransactionIsApplied() {
            BankTransactionDTO first = bankTransaction(
                    101L,
                    "Hong Gil Dong",
                    LocalDateTime.of(2026, 8, 5, 10, 0)
            );
            BankTransactionDTO second = bankTransaction(
                    102L,
                    "Hong Gil Dong",
                    LocalDateTime.of(2026, 8, 5, 11, 0)
            );
            MatchingCandidate candidate = candidate();

            given(bankTransactionService
                    .findPendingDepositsByLinkedAccountId(LINKED_ACCOUNT_ID))
                    .willReturn(List.of(first, second));
            given(paymentObligationMapper
                    .findMatchCandidatesByLinkedAccountId(
                            LINKED_ACCOUNT_ID,
                            first.getTransactionAt()
                    ))
                    .willReturn(List.of(candidate));
            given(paymentObligationMapper
                    .findMatchCandidatesByLinkedAccountId(
                            LINKED_ACCOUNT_ID,
                            second.getTransactionAt()
                    ))
                    .willReturn(List.of());
            given(autoMatchingService.execute(any(), any()))
                    .willReturn(
                            executionResult(
                                    result(
                                            101L,
                                            AutoMatchingProcessStatus.APPLIED
                                    )
                            ),
                            executionResult(
                                    result(
                                            102L,
                                            AutoMatchingProcessStatus.UNMATCHED
                                    )
                            )
                    );

            AutoMatchingExecutionResult result =
                    bankMatchingService.execute(LINKED_ACCOUNT_ID);

            ArgumentCaptor<List<MatchingCandidate>> candidatesCaptor =
                    ArgumentCaptor.forClass(List.class);
            verify(autoMatchingService, org.mockito.Mockito.times(2))
                    .execute(any(), candidatesCaptor.capture());

            assertThat(candidatesCaptor.getAllValues())
                    .containsExactly(List.of(candidate), List.of());
            assertThat(result.transactionResults())
                    .extracting(
                            AutoMatchingTransactionResult::transactionId,
                            AutoMatchingTransactionResult::processStatus
                    )
                    .containsExactly(
                            tuple(101L, AutoMatchingProcessStatus.APPLIED),
                            tuple(102L, AutoMatchingProcessStatus.UNMATCHED)
                    );
            verify(bankTransactionService).updateStatus(
                    101L,
                    BankTransactionProcessingStatus.PENDING,
                    BankTransactionProcessingStatus.APPLIED
            );
            verify(bankTransactionService).updateStatus(
                    102L,
                    BankTransactionProcessingStatus.PENDING,
                    BankTransactionProcessingStatus.UNMATCHED
            );
        }

        @Test
        @DisplayName("자동매칭 결과별 은행 거래 상태를 변경한다")
        void updatesBankTransactionStatusByResultStatus() {
            BankTransactionDTO applied = bankTransaction(
                    101L,
                    "Hong Gil Dong"
            );
            BankTransactionDTO needsCheck = bankTransaction(
                    102L,
                    "Kim Chul Soo"
            );
            BankTransactionDTO unmatched = bankTransaction(
                    103L,
                    "Lee Young Hee"
            );
            BankTransactionDTO failed = bankTransaction(
                    104L,
                    "Park Min Soo"
            );

            given(bankTransactionService
                    .findPendingDepositsByLinkedAccountId(LINKED_ACCOUNT_ID))
                    .willReturn(List.of(
                            applied,
                            needsCheck,
                            unmatched,
                            failed
                    ));
            given(paymentObligationMapper
                    .findMatchCandidatesByLinkedAccountId(any(), any()))
                    .willReturn(List.of(candidate()));
            given(autoMatchingService.execute(any(), any()))
                    .willReturn(
                            executionResult(
                                    result(
                                            101L,
                                            AutoMatchingProcessStatus.APPLIED
                                    )
                            ),
                            executionResult(
                                    result(
                                            102L,
                                            AutoMatchingProcessStatus.NEEDS_CHECK
                                    )
                            ),
                            executionResult(
                                    result(
                                            103L,
                                            AutoMatchingProcessStatus.UNMATCHED
                                    )
                            ),
                            executionResult(
                                    result(
                                            104L,
                                            AutoMatchingProcessStatus.FAILED
                                    )
                            )
                    );

            bankMatchingService.execute(LINKED_ACCOUNT_ID);

            verify(bankTransactionService).updateStatus(
                    101L,
                    BankTransactionProcessingStatus.PENDING,
                    BankTransactionProcessingStatus.APPLIED
            );
            verify(bankTransactionService).updateStatus(
                    102L,
                    BankTransactionProcessingStatus.PENDING,
                    BankTransactionProcessingStatus.NEEDS_CHECK
            );
            verify(bankTransactionService).updateStatus(
                    103L,
                    BankTransactionProcessingStatus.PENDING,
                    BankTransactionProcessingStatus.UNMATCHED
            );
            verify(bankTransactionService).updateStatus(
                    104L,
                    BankTransactionProcessingStatus.PENDING,
                    BankTransactionProcessingStatus.FAILED
            );
        }

        @Test
        @DisplayName("중복 결과는 자동 반영 상태로 저장한다")
        void updatesDuplicatedResultAsApplied() {
            BankTransactionDTO transaction = bankTransaction(
                    101L,
                    "Hong Gil Dong"
            );

            given(bankTransactionService
                    .findPendingDepositsByLinkedAccountId(LINKED_ACCOUNT_ID))
                    .willReturn(List.of(transaction));
            given(paymentObligationMapper
                    .findMatchCandidatesByLinkedAccountId(
                            LINKED_ACCOUNT_ID,
                            transaction.getTransactionAt()
                    ))
                    .willReturn(List.of(candidate()));
            given(autoMatchingService.execute(any(), any()))
                    .willReturn(executionResult(
                            result(
                                    101L,
                                    AutoMatchingProcessStatus.DUPLICATE
                            )
                    ));

            bankMatchingService.execute(LINKED_ACCOUNT_ID);

            verify(bankTransactionService).updateStatus(
                    101L,
                    BankTransactionProcessingStatus.PENDING,
                    BankTransactionProcessingStatus.APPLIED
            );
        }

        @Test
        @DisplayName("상태 변경 실패 예외는 그대로 전파한다")
        void propagatesStatusUpdateFailure() {
            BankTransactionDTO transaction = bankTransaction(
                    101L,
                    "Hong Gil Dong"
            );

            given(bankTransactionService
                    .findPendingDepositsByLinkedAccountId(LINKED_ACCOUNT_ID))
                    .willReturn(List.of(transaction));
            given(paymentObligationMapper
                    .findMatchCandidatesByLinkedAccountId(
                            LINKED_ACCOUNT_ID,
                            transaction.getTransactionAt()
                    ))
                    .willReturn(List.of(candidate()));
            given(autoMatchingService.execute(any(), any()))
                    .willReturn(executionResult(
                            result(101L, AutoMatchingProcessStatus.FAILED)
                    ));
            willThrow(BankTransactionErrorCode
                    .BANK_TRANSACTION_STATUS_UPDATE_FAILED
                    .toException())
                    .given(bankTransactionService)
                    .updateStatus(
                            101L,
                            BankTransactionProcessingStatus.PENDING,
                            BankTransactionProcessingStatus.FAILED
                    );

            assertThatThrownBy(
                    () -> bankMatchingService.execute(LINKED_ACCOUNT_ID)
            ).isInstanceOf(DomainException.class);
        }

        @Test
        @DisplayName("연결 계좌 ID가 올바르지 않으면 예외가 발생한다")
        void throwsExceptionWhenLinkedAccountIdIsInvalid() {
            assertThatThrownBy(() -> bankMatchingService.execute(0L))
                    .isInstanceOfSatisfying(
                            DomainException.class,
                            exception -> assertThat(exception.getErrorCode())
                                    .isEqualTo(
                                            MatchingErrorCode
                                                    .INVALID_MATCHING_REQUEST
                                    )
                    );
        }
    }

    private BankTransactionDTO bankTransaction(
            Long bankTransactionId,
            String counterpartyName
    ) {
        return bankTransaction(
                bankTransactionId,
                counterpartyName,
                LocalDateTime.of(2026, 8, 5, 10, 0)
        );
    }

    private BankTransactionDTO bankTransaction(
            Long bankTransactionId,
            String counterpartyName,
            LocalDateTime transactionAt
    ) {
        return BankTransactionDTO.builder()
                .bankTransactionId(bankTransactionId)
                .linkedAccountId(LINKED_ACCOUNT_ID)
                .externalTransactionId("external-" + bankTransactionId)
                .amount(new BigDecimal("10000.00"))
                .transactionType(BankTransactionType.DEPOSIT)
                .processingStatus(BankTransactionProcessingStatus.PENDING)
                .transactionAt(transactionAt)
                .counterpartyName(counterpartyName)
                .syncedAt(LocalDateTime.of(2026, 8, 5, 10, 5))
                .build();
    }

    private MatchingCandidate candidate() {
        return new MatchingCandidate(
                MatchingTargetType.SETTLEMENT,
                1L,
                1L,
                "Hong Gil Dong",
                new BigDecimal("10000.00")
        );
    }

    private AutoMatchingTransactionResult result(
            Long transactionId,
            AutoMatchingProcessStatus processStatus
    ) {
        return new AutoMatchingTransactionResult(
                transactionId,
                processStatus
        );
    }

    private AutoMatchingExecutionResult executionResult(
            AutoMatchingTransactionResult... transactionResults
    ) {
        int appliedCount = 0;
        int needsCheckCount = 0;
        int unmatchedCount = 0;
        int duplicateCount = 0;
        int failedCount = 0;

        for (AutoMatchingTransactionResult transactionResult
                : transactionResults) {
            switch (transactionResult.processStatus()) {
                case APPLIED -> appliedCount++;
                case NEEDS_CHECK -> needsCheckCount++;
                case UNMATCHED -> unmatchedCount++;
                case DUPLICATE -> duplicateCount++;
                case FAILED -> failedCount++;
            }
        }

        return new AutoMatchingExecutionResult(
                transactionResults.length,
                appliedCount,
                needsCheckCount,
                unmatchedCount,
                duplicateCount,
                failedCount,
                List.of(transactionResults)
        );
    }
}

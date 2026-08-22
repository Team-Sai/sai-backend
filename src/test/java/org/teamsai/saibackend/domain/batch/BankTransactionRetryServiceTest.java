package org.teamsai.saibackend.domain.batch;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.teamsai.saibackend.domain.batch.common.notification.SlackNotifier;
import org.teamsai.saibackend.domain.matching.mapper.BankTransactionMatchCandidateMapper;
import org.teamsai.saibackend.domain.matching.model.AutoMatchingExecutionResult;
import org.teamsai.saibackend.domain.matching.service.BankMatchingService;
import org.teamsai.saibackend.domain.matching.service.BankTransactionRetryService;
import org.teamsai.saibackend.domain.matching.type.RetryPolicy;
import org.teamsai.saibackend.domain.transaction.dto.BankTransactionDTO;
import org.teamsai.saibackend.domain.transaction.mapper.BankTransactionMapper;
import org.teamsai.saibackend.domain.transaction.type.BankTransactionProcessingStatus;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BankTransactionRetryServiceTest {

    @Mock
    private BankTransactionMapper bankTransactionMapper;
    @Mock
    private BankTransactionMatchCandidateMapper candidateMapper;
    @Mock
    private BankMatchingService bankMatchingService;
    @Mock
    private SlackNotifier slackNotifier;

    @InjectMocks
    private BankTransactionRetryService service;

    private final Long userId = 1L;
    private final Long linkedAccountId = 10L;

    private BankTransactionDTO tx(Long id, BankTransactionProcessingStatus status, int retryCount) {
        return BankTransactionDTO.builder()
                .bankTransactionId(id)
                .linkedAccountId(linkedAccountId)
                .processingStatus(status)
                .retryCount(retryCount)
                .build();
    }

    @Nested
    class NoCandidates {

        @Test
        void 재시도_대상이_없으면_아무것도_하지_않는다() {
            when(bankTransactionMapper.findRetryCandidates(linkedAccountId))
                    .thenReturn(Collections.emptyList());

            service.retryForAccount(userId, linkedAccountId);

            verifyNoInteractions(candidateMapper, bankMatchingService, slackNotifier);
            verify(bankTransactionMapper, never()).resetToPendingForRetry(any(), any());
        }
    }

    @Nested
    class RetryFlow {

        @Test
        void 대상이_있으면_기존_후보_삭제하고_PENDING으로_리셋한_뒤_매칭을_실행한다() {
            BankTransactionDTO tx1 = tx(100L, BankTransactionProcessingStatus.UNMATCHED, 1);
            BankTransactionDTO tx2 = tx(200L, BankTransactionProcessingStatus.UNMATCHED, 2);
            List<BankTransactionDTO> candidates = List.of(tx1, tx2);

            when(bankTransactionMapper.findRetryCandidates(linkedAccountId)).thenReturn(candidates);

            AutoMatchingExecutionResult result = mock(AutoMatchingExecutionResult.class);
            when(result.appliedCount()).thenReturn(1);
            when(bankMatchingService.execute(userId, linkedAccountId, true)).thenReturn(result);

            // checkAndNotifyExhausted 쪽에서 재조회 - 성공 처리된 것으로 가정해 알림 없게
            when(bankTransactionMapper.findById(100L))
                    .thenReturn(Optional.of(tx(100L, BankTransactionProcessingStatus.APPLIED, 1)));
            when(bankTransactionMapper.findById(200L))
                    .thenReturn(Optional.of(tx(200L, BankTransactionProcessingStatus.APPLIED, 2)));

            service.retryForAccount(userId, linkedAccountId);

            verify(candidateMapper).deleteAllByBankTransactionId(100L);
            verify(candidateMapper).deleteAllByBankTransactionId(200L);
            verify(bankTransactionMapper).resetToPendingForRetry(100L, BankTransactionProcessingStatus.UNMATCHED);
            verify(bankTransactionMapper).resetToPendingForRetry(200L, BankTransactionProcessingStatus.UNMATCHED);
            verify(bankMatchingService).execute(userId, linkedAccountId, true);
            verifyNoInteractions(slackNotifier);
        }
    }

    @Nested
    class ExhaustionNotification {

        @Test
        void 재조회_결과가_APPLIED면_알림을_보내지_않는다() {
            BankTransactionDTO original = tx(100L, BankTransactionProcessingStatus.UNMATCHED, 3);
            when(bankTransactionMapper.findRetryCandidates(linkedAccountId)).thenReturn(List.of(original));

            AutoMatchingExecutionResult result = mock(AutoMatchingExecutionResult.class);
            when(bankMatchingService.execute(userId, linkedAccountId, true)).thenReturn(result);

            when(bankTransactionMapper.findById(100L))
                    .thenReturn(Optional.of(tx(100L, BankTransactionProcessingStatus.APPLIED, 3)));

            service.retryForAccount(userId, linkedAccountId);

            verifyNoInteractions(slackNotifier);
        }

        @Test
        void 재조회_결과가_없으면_알림을_보내지_않는다() {
            BankTransactionDTO original = tx(100L, BankTransactionProcessingStatus.UNMATCHED, 3);
            when(bankTransactionMapper.findRetryCandidates(linkedAccountId)).thenReturn(List.of(original));

            AutoMatchingExecutionResult result = mock(AutoMatchingExecutionResult.class);
            when(bankMatchingService.execute(userId, linkedAccountId, true)).thenReturn(result);

            when(bankTransactionMapper.findById(100L)).thenReturn(Optional.empty());

            service.retryForAccount(userId, linkedAccountId);

            verifyNoInteractions(slackNotifier);
        }

        @Test
        void 재시도횟수가_최대치_이상이고_APPLIED가_아니면_알림을_보낸다() {
            BankTransactionDTO original = tx(100L, BankTransactionProcessingStatus.UNMATCHED, 5);
            when(bankTransactionMapper.findRetryCandidates(linkedAccountId)).thenReturn(List.of(original));

            AutoMatchingExecutionResult result = mock(AutoMatchingExecutionResult.class);
            when(bankMatchingService.execute(userId, linkedAccountId, true)).thenReturn(result);

            BankTransactionDTO stillUnmatched = tx(100L, BankTransactionProcessingStatus.UNMATCHED, 5);
            when(bankTransactionMapper.findById(100L)).thenReturn(Optional.of(stillUnmatched));

            try (MockedStatic<RetryPolicy> mockedPolicy = mockStatic(RetryPolicy.class)) {
                mockedPolicy.when(() -> RetryPolicy.maxRetryCount(BankTransactionProcessingStatus.UNMATCHED))
                        .thenReturn(5);

                service.retryForAccount(userId, linkedAccountId);

                verify(slackNotifier, times(1)).send(any());
            }
        }

        @Test
        void 재시도횟수가_최대치_미만이면_알림을_보내지_않는다() {
            BankTransactionDTO original = tx(100L, BankTransactionProcessingStatus.UNMATCHED, 2);
            when(bankTransactionMapper.findRetryCandidates(linkedAccountId)).thenReturn(List.of(original));

            AutoMatchingExecutionResult result = mock(AutoMatchingExecutionResult.class);
            when(bankMatchingService.execute(userId, linkedAccountId, true)).thenReturn(result);

            BankTransactionDTO stillUnmatched = tx(100L, BankTransactionProcessingStatus.UNMATCHED, 2);
            when(bankTransactionMapper.findById(100L)).thenReturn(Optional.of(stillUnmatched));

            try (MockedStatic<RetryPolicy> mockedPolicy = mockStatic(RetryPolicy.class)) {
                mockedPolicy.when(() -> RetryPolicy.maxRetryCount(BankTransactionProcessingStatus.UNMATCHED))
                        .thenReturn(5);

                service.retryForAccount(userId, linkedAccountId);

                verifyNoInteractions(slackNotifier);
            }
        }

        @Test
        void 여러건_중_일부만_소진되면_소진된_건만_알림을_보낸다() {
            BankTransactionDTO tx1 = tx(100L, BankTransactionProcessingStatus.UNMATCHED, 5);
            BankTransactionDTO tx2 = tx(200L, BankTransactionProcessingStatus.UNMATCHED, 1);
            when(bankTransactionMapper.findRetryCandidates(linkedAccountId)).thenReturn(List.of(tx1, tx2));

            AutoMatchingExecutionResult result = mock(AutoMatchingExecutionResult.class);
            when(bankMatchingService.execute(userId, linkedAccountId, true)).thenReturn(result);

            when(bankTransactionMapper.findById(100L))
                    .thenReturn(Optional.of(tx(100L, BankTransactionProcessingStatus.UNMATCHED, 5)));
            when(bankTransactionMapper.findById(200L))
                    .thenReturn(Optional.of(tx(200L, BankTransactionProcessingStatus.UNMATCHED, 1)));

            try (MockedStatic<RetryPolicy> mockedPolicy = mockStatic(RetryPolicy.class)) {
                mockedPolicy.when(() -> RetryPolicy.maxRetryCount(BankTransactionProcessingStatus.UNMATCHED))
                        .thenReturn(5);

                service.retryForAccount(userId, linkedAccountId);

                verify(slackNotifier, times(1)).send(any());
            }
        }
    }
}
package org.teamsai.saibackend.domain.transaction;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.teamsai.saibackend.domain.transaction.mapper.BankTransactionMapper;
import org.teamsai.saibackend.domain.transaction.service.BankTransactionService;
import org.teamsai.saibackend.domain.transaction.type.BankTransactionProcessingStatus;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("BankTransactionService 단위 테스트")
class BankTransactionServiceTest {

    @Mock
    private BankTransactionMapper bankTransactionMapper;

    @InjectMocks
    private BankTransactionService bankTransactionService;

    @Test
    @DisplayName("확인 필요 거래를 반영 완료 상태로 변경할 수 있다")
    void updatesNeedsCheckTransactionToApplied() {
        given(bankTransactionMapper.updateStatus(
                1L,
                BankTransactionProcessingStatus.NEEDS_CHECK,
                BankTransactionProcessingStatus.APPLIED
        )).willReturn(1);

        bankTransactionService.updateStatus(
                1L,
                BankTransactionProcessingStatus.NEEDS_CHECK,
                BankTransactionProcessingStatus.APPLIED
        );

        verify(bankTransactionMapper).updateStatus(
                1L,
                BankTransactionProcessingStatus.NEEDS_CHECK,
                BankTransactionProcessingStatus.APPLIED
        );
    }

    @Test
    @DisplayName("확인 필요 거래를 미매칭 상태로 변경할 수 있다")
    void updatesNeedsCheckTransactionToUnmatched() {
        given(bankTransactionMapper.updateStatus(
                1L,
                BankTransactionProcessingStatus.NEEDS_CHECK,
                BankTransactionProcessingStatus.UNMATCHED
        )).willReturn(1);

        bankTransactionService.updateStatus(
                1L,
                BankTransactionProcessingStatus.NEEDS_CHECK,
                BankTransactionProcessingStatus.UNMATCHED
        );

        verify(bankTransactionMapper).updateStatus(
                1L,
                BankTransactionProcessingStatus.NEEDS_CHECK,
                BankTransactionProcessingStatus.UNMATCHED
        );
    }
}

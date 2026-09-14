package org.teamsai.saibackend.domain.transaction;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.teamsai.saibackend.domain.transaction.dto.BankTransactionDTO;
import org.teamsai.saibackend.domain.transaction.exception.BankTransactionErrorCode;
import org.teamsai.saibackend.domain.transaction.mapper.BankTransactionMapper;
import org.teamsai.saibackend.domain.transaction.service.BankTransactionService;
import org.teamsai.saibackend.domain.transaction.type.BankTransactionProcessingStatus;
import org.teamsai.saibackend.global.exception.DomainException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
    @DisplayName("연결계좌와 거래 ID로 은행 거래를 잠금 조회한다")
    void findsTransactionByIdAndLinkedAccountIdForUpdate() {
        BankTransactionDTO transaction = BankTransactionDTO.builder()
                .bankTransactionId(101L)
                .linkedAccountId(1L)
                .processingStatus(BankTransactionProcessingStatus.PENDING)
                .build();
        given(bankTransactionMapper.findByIdAndLinkedAccountIdForUpdate(
                101L,
                1L
        )).willReturn(Optional.of(transaction));

        BankTransactionDTO result = bankTransactionService
                .findByIdAndLinkedAccountIdForUpdate(101L, 1L);

        assertThat(result).isSameAs(transaction);
    }

    @Test
    @DisplayName("잠금 조회할 은행 거래가 없으면 예외가 발생한다")
    void throwsWhenTransactionForUpdateDoesNotExist() {
        given(bankTransactionMapper.findByIdAndLinkedAccountIdForUpdate(
                101L,
                1L
        )).willReturn(Optional.empty());

        assertThatThrownBy(() -> bankTransactionService
                .findByIdAndLinkedAccountIdForUpdate(101L, 1L)
        ).isInstanceOfSatisfying(
                DomainException.class,
                exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(
                                BankTransactionErrorCode
                                        .BANK_TRANSACTION_NOT_FOUND
                        )
        );
    }

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

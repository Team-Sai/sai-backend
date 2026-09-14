package org.teamsai.saibackend.domain.settlement;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.teamsai.saibackend.domain.payment.dto.PaymentRecordDTO;
import org.teamsai.saibackend.domain.payment.service.PaymentRecordService;
import org.teamsai.saibackend.domain.payment.type.PaymentTargetType;
import org.teamsai.saibackend.domain.payment.type.RecordStatus;
import org.teamsai.saibackend.domain.payment.type.SourceType;
import org.teamsai.saibackend.domain.settlement.dto.response.SettlementPaymentHistoryResponse;
import org.teamsai.saibackend.domain.settlement.dto.response.SettlementPaymentObligationResponse;
import org.teamsai.saibackend.domain.settlement.dto.response.SettlementPaymentStatusResponse;
import org.teamsai.saibackend.domain.settlement.service.SettlementPaymentHistoryService;
import org.teamsai.saibackend.domain.settlement.service.SettlementPaymentStatusService;
import org.teamsai.saibackend.domain.transaction.dto.BankTransactionDTO;
import org.teamsai.saibackend.domain.transaction.mapper.BankTransactionMapper;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("SettlementPaymentHistoryService 단위 테스트")
class SettlementPaymentHistoryServiceTest {

    private static final Long SETTLEMENT_ID = 1L;
    private static final Long USER_ID = 10L;

    @Mock
    private SettlementPaymentStatusService settlementPaymentStatusService;

    @Mock
    private PaymentRecordService paymentRecordService;

    @Mock
    private BankTransactionMapper bankTransactionMapper;

    @InjectMocks
    private SettlementPaymentHistoryService settlementPaymentHistoryService;

    @Test
    @DisplayName("납부의무-참여자 이름과 연결된 거래정보를 조합해 납부 내역을 만든다")
    void getPaymentHistoryComposesPayerNameAndTransactionInfo() {

        given(
                settlementPaymentStatusService.getPaymentStatus(
                        SETTLEMENT_ID,
                        USER_ID
                )
        ).willReturn(
                statusWithObligations(
                        obligation(100L, "홍길동")
                )
        );

        given(
                paymentRecordService.findConfirmedRecordsByTargetIds(
                        eq(PaymentTargetType.SETTLEMENT),
                        anyList()
                )
        ).willReturn(
                List.of(
                        paymentRecord(1L, 100L, 200L, "10000")
                )
        );

        given(
                bankTransactionMapper.findById(200L)
        ).willReturn(
                Optional.of(
                        bankTransaction(200L, "카카오뱅크 홍길동", "TX-EXTERNAL-1")
                )
        );


        List<SettlementPaymentHistoryResponse> result =
                settlementPaymentHistoryService.getPaymentHistory(
                        SETTLEMENT_ID,
                        USER_ID
                );


        assertThat(result).hasSize(1);

        SettlementPaymentHistoryResponse history = result.get(0);
        assertThat(history.payerName()).isEqualTo("홍길동");
        assertThat(history.amount()).isEqualByComparingTo("10000");
        assertThat(history.sourceType()).isEqualTo(SourceType.AUTO_MATCH);
        assertThat(history.bankTransactionId()).isEqualTo(200L);
        assertThat(history.counterpartyName()).isEqualTo("카카오뱅크 홍길동");
        assertThat(history.externalTransactionId()).isEqualTo("TX-EXTERNAL-1");
    }

    @Test
    @DisplayName("연결된 은행거래를 찾을 수 없으면 거래정보는 null로 채워진다")
    void getPaymentHistoryFillsNullWhenBankTransactionNotFound() {

        given(
                settlementPaymentStatusService.getPaymentStatus(
                        SETTLEMENT_ID,
                        USER_ID
                )
        ).willReturn(
                statusWithObligations(
                        obligation(100L, "홍길동")
                )
        );

        given(
                paymentRecordService.findConfirmedRecordsByTargetIds(
                        eq(PaymentTargetType.SETTLEMENT),
                        anyList()
                )
        ).willReturn(
                List.of(
                        paymentRecord(1L, 100L, 999L, "10000")
                )
        );

        given(
                bankTransactionMapper.findById(999L)
        ).willReturn(
                Optional.empty()
        );


        List<SettlementPaymentHistoryResponse> result =
                settlementPaymentHistoryService.getPaymentHistory(
                        SETTLEMENT_ID,
                        USER_ID
                );


        assertThat(result).hasSize(1);

        SettlementPaymentHistoryResponse history = result.get(0);
        assertThat(history.payerName()).isEqualTo("홍길동");
        assertThat(history.counterpartyName()).isNull();
        assertThat(history.externalTransactionId()).isNull();
    }

    @Test
    @DisplayName("납부의무 ID 목록을 대상으로 확정 납부기록을 조회한다")
    void getPaymentHistoryQueriesRecordsByObligationIds() {

        given(
                settlementPaymentStatusService.getPaymentStatus(
                        SETTLEMENT_ID,
                        USER_ID
                )
        ).willReturn(
                statusWithObligations(
                        obligation(100L, "홍길동"),
                        obligation(101L, "김철수")
                )
        );

        given(
                paymentRecordService.findConfirmedRecordsByTargetIds(
                        eq(PaymentTargetType.SETTLEMENT),
                        anyList()
                )
        ).willReturn(
                List.of()
        );


        settlementPaymentHistoryService.getPaymentHistory(
                SETTLEMENT_ID,
                USER_ID
        );


        verify(paymentRecordService).findConfirmedRecordsByTargetIds(
                eq(PaymentTargetType.SETTLEMENT),
                argThatContainsExactlyInAnyOrder(100L, 101L)
        );
    }

    private static List<Long> argThatContainsExactlyInAnyOrder(Long... ids) {
        return org.mockito.ArgumentMatchers.argThat(list ->
                list != null
                        && list.size() == ids.length
                        && list.containsAll(List.of(ids))
        );
    }

    private SettlementPaymentStatusResponse statusWithObligations(
            SettlementPaymentObligationResponse... obligations
    ) {
        return SettlementPaymentStatusResponse.builder()
                .settlementId(SETTLEMENT_ID)
                .obligations(List.of(obligations))
                .build();
    }

    private SettlementPaymentObligationResponse obligation(Long paymentObligationId, String participantName) {
        return SettlementPaymentObligationResponse.builder()
                .paymentObligationId(paymentObligationId)
                .participantName(participantName)
                .build();
    }

    private PaymentRecordDTO paymentRecord(
            Long paymentRecordId,
            Long targetId,
            Long bankTransactionId,
            String amount
    ) {
        return PaymentRecordDTO.builder()
                .paymentRecordId(paymentRecordId)
                .bankTransactionId(bankTransactionId)
                .paymentTargetType(PaymentTargetType.SETTLEMENT)
                .targetId(targetId)
                .amount(new BigDecimal(amount))
                .sourceType(SourceType.AUTO_MATCH)
                .recordStatus(RecordStatus.CONFIRMED)
                .recordedAt(LocalDateTime.of(2026, 8, 18, 12, 0))
                .build();
    }

    private BankTransactionDTO bankTransaction(
            Long bankTransactionId,
            String counterpartyName,
            String externalTransactionId
    ) {
        return BankTransactionDTO.builder()
                .bankTransactionId(bankTransactionId)
                .counterpartyName(counterpartyName)
                .externalTransactionId(externalTransactionId)
                .build();
    }
}

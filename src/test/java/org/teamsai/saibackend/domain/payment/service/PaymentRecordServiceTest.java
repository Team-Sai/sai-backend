package org.teamsai.saibackend.domain.payment.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.teamsai.saibackend.domain.payment.dto.PaymentRecordDTO;
import org.teamsai.saibackend.domain.payment.mapper.PaymentRecordMapper;
import org.teamsai.saibackend.domain.payment.type.PaymentTargetType;
import org.teamsai.saibackend.domain.payment.type.RecordStatus;
import org.teamsai.saibackend.domain.payment.type.SourceType;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("PaymentRecordService 단위 테스트")
class PaymentRecordServiceTest {

    @Mock
    private PaymentRecordMapper paymentRecordMapper;

    @InjectMocks
    private PaymentRecordService paymentRecordService;

    @Test
    @DisplayName("targetId 목록으로 확정된 납부기록을 조회한다")
    void findConfirmedRecordsByTargetIdsDelegatesToMapper() {

        List<Long> targetIds = List.of(1L, 2L);

        List<PaymentRecordDTO> records = List.of(
                record(1L, 1L, "10000"),
                record(2L, 2L, "20000")
        );

        given(
                paymentRecordMapper.findConfirmedByTargetIds(
                        PaymentTargetType.SETTLEMENT,
                        targetIds
                )
        ).willReturn(records);


        List<PaymentRecordDTO> result =
                paymentRecordService.findConfirmedRecordsByTargetIds(
                        PaymentTargetType.SETTLEMENT,
                        targetIds
                );


        assertThat(result).isEqualTo(records);
    }

    @Test
    @DisplayName("targetId 목록이 비어있으면 매퍼를 호출하지 않고 빈 목록을 반환한다")
    void findConfirmedRecordsByTargetIdsReturnsEmptyListWhenTargetIdsIsEmpty() {

        List<PaymentRecordDTO> result =
                paymentRecordService.findConfirmedRecordsByTargetIds(
                        PaymentTargetType.SETTLEMENT,
                        Collections.emptyList()
                );


        assertThat(result).isEmpty();

        verify(paymentRecordMapper, never())
                .findConfirmedByTargetIds(any(), any());
    }

    @Test
    @DisplayName("targetId 목록이 null이면 매퍼를 호출하지 않고 빈 목록을 반환한다")
    void findConfirmedRecordsByTargetIdsReturnsEmptyListWhenTargetIdsIsNull() {

        List<PaymentRecordDTO> result =
                paymentRecordService.findConfirmedRecordsByTargetIds(
                        PaymentTargetType.SETTLEMENT,
                        null
                );


        assertThat(result).isEmpty();

        verify(paymentRecordMapper, never())
                .findConfirmedByTargetIds(any(), any());
    }

    private PaymentRecordDTO record(Long paymentRecordId, Long targetId, String amount) {
        return PaymentRecordDTO.builder()
                .paymentRecordId(paymentRecordId)
                .bankTransactionId(100L + paymentRecordId)
                .paymentTargetType(PaymentTargetType.SETTLEMENT)
                .targetId(targetId)
                .amount(new BigDecimal(amount))
                .sourceType(SourceType.MANUAL)
                .recordStatus(RecordStatus.CONFIRMED)
                .recordedAt(LocalDateTime.of(2026, 8, 18, 12, 0))
                .build();
    }
}

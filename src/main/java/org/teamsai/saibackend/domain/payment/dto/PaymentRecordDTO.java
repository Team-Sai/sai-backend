package org.teamsai.saibackend.domain.payment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.teamsai.saibackend.domain.payment.type.RecordStatus;
import org.teamsai.saibackend.domain.payment.type.SourceType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentRecordDTO {
    private Long paymentRecordId;

    private Long bankTransactionId;
    private Long obligationId;

    private BigDecimal amount;

    private SourceType sourceType;
    private RecordStatus recordStatus;

    private LocalDateTime recordedAt;

    private Long cancelledById;
    private LocalDateTime cancelledAt;

    private String memo;
}

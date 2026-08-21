package org.teamsai.saibackend.domain.settlement.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.teamsai.saibackend.domain.payment.type.ObligationStatus;
import org.teamsai.saibackend.domain.payment.type.PaymentStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SettlementPaymentObligationResponse {

    private Long paymentObligationId;
    private Long participantId;
    @JsonIgnore
    private Long userId;
    private String participantName;
    private BigDecimal expectedAmount;
    private BigDecimal paidAmount;
    private BigDecimal remainingAmount;
    private LocalDateTime latestPaymentAt;

    private PaymentStatus paymentStatus;
    private ObligationStatus obligationStatus;
}

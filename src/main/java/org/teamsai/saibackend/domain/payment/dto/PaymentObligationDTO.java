package org.teamsai.saibackend.domain.payment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.teamsai.saibackend.domain.payment.type.ObligationStatus;
import org.teamsai.saibackend.domain.payment.type.PaymentStatus;
import org.teamsai.saibackend.domain.payment.type.ReviewStatus;

import java.math.BigDecimal;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentObligationDTO {

    private Long paymentObligationId;
    private Long participantId;
    private BigDecimal expectedAmount;
    private PaymentStatus paymentStatus;
    private ReviewStatus reviewStatus;
    private ObligationStatus obligationStatus;
}

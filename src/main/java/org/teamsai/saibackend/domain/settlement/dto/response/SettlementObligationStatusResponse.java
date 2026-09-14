package org.teamsai.saibackend.domain.settlement.dto.response;

import org.teamsai.saibackend.domain.payment.type.ObligationStatus;

import java.math.BigDecimal;

public record SettlementObligationStatusResponse(
        Long paymentObligationId,
        ObligationStatus obligationStatus,
        BigDecimal expectedAmount,
        BigDecimal paidAmount
) {}
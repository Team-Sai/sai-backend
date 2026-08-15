package org.teamsai.saibackend.domain.settlement.service;

import org.springframework.stereotype.Component;
import org.teamsai.saibackend.domain.settlement.exception.SettlementErrorCode;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Component
public class SettlementAmountCalculator {

    private static final int WON_SCALE = 0;

    public BigDecimal calculateEqualAmount(BigDecimal totalAmount, int participantCount) {
        return calculateEqualAmountForTotalCount(totalAmount, participantCount + 1);
    }

    public BigDecimal calculateEqualAmountForTotalCount(
            BigDecimal totalAmount,
            int totalParticipantCount
    ) {
        if (totalAmount == null || totalAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw SettlementErrorCode.INVALID_SETTLEMENT_AMOUNT.toException();
        }
        BigDecimal perPersonAmount = totalAmount.divide(
                BigDecimal.valueOf(totalParticipantCount), WON_SCALE, RoundingMode.DOWN);
        if (perPersonAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw SettlementErrorCode.INVALID_SETTLEMENT_AMOUNT.toException();
        }
        return perPersonAmount;
    }
}

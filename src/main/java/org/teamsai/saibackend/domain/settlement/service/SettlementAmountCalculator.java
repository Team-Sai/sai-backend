package org.teamsai.saibackend.domain.settlement.service;

import org.springframework.stereotype.Component;
import org.teamsai.saibackend.domain.settlement.exception.SettlementErrorCode;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

@Component
public class SettlementAmountCalculator {

    private static final int WON_SCALE = 0;

    public BigDecimal calculateEqualAmount(BigDecimal totalAmount, int participantCount) {
        return calculateEqualAmountForTotalCount(totalAmount, participantCount + 1);
    }

    public BigDecimal calculateEqualAmountForTotalCount(BigDecimal totalAmount, int totalParticipantCount) {
        validateInput(totalAmount, totalParticipantCount);

        BigDecimal perPersonAmount = totalAmount.divide(
                BigDecimal.valueOf(totalParticipantCount), WON_SCALE, RoundingMode.DOWN);

        if (perPersonAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw SettlementErrorCode.INVALID_SETTLEMENT_AMOUNT.toException();
        }
        return perPersonAmount;
    }

    /**
     * 총액을 totalParticipantCount명에게 균등 분배하되, 나머지(반올림 손실분)는
     * 마지막 참여자에게 배정하여 합계가 totalAmount와 정확히 일치하도록 한다.
     */
    public List<BigDecimal> distributeEqualAmounts(BigDecimal totalAmount, int totalParticipantCount) {
        validateInput(totalAmount, totalParticipantCount);

        BigDecimal baseAmount = totalAmount.divide(
                BigDecimal.valueOf(totalParticipantCount), WON_SCALE, RoundingMode.DOWN);
        BigDecimal remainder = totalAmount.subtract(
                baseAmount.multiply(BigDecimal.valueOf(totalParticipantCount)));

        List<BigDecimal> amounts = new ArrayList<>();
        for (int i = 0; i < totalParticipantCount; i++) {
            amounts.add(baseAmount);
        }
        int lastIndex = totalParticipantCount - 1;
        amounts.set(lastIndex, amounts.get(lastIndex).add(remainder));

        return amounts;
    }

    private void validateInput(BigDecimal totalAmount, int totalParticipantCount) {
        if (totalAmount == null || totalAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw SettlementErrorCode.INVALID_SETTLEMENT_AMOUNT.toException();
        }
        if (totalParticipantCount <= 0) {
            throw SettlementErrorCode.INVALID_SETTLEMENT_AMOUNT.toException();
        }
    }
}
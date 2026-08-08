package org.teamsai.saibackend.domain.settlement.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.teamsai.saibackend.domain.settlement.dto.SettlementDTO;
import org.teamsai.saibackend.domain.settlement.dto.response.SettlementPaymentObligationResponse;
import org.teamsai.saibackend.domain.settlement.dto.response.SettlementPaymentStatusResponse;
import org.teamsai.saibackend.domain.settlement.exception.SettlementErrorCode;
import org.teamsai.saibackend.domain.settlement.mapper.SettlementMapper;
import org.teamsai.saibackend.domain.settlement.mapper.SettlementPaymentStatusMapper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SettlementPaymentStatusService {

    private static final BigDecimal HUNDRED =
            BigDecimal.valueOf(100);
    private static final int RATE_SCALE = 2;

    private final SettlementMapper settlementMapper;
    private final SettlementPaymentStatusMapper
            paymentStatusMapper;

    @Transactional(readOnly = true)
    public SettlementPaymentStatusResponse getPaymentStatus(
            Long settlementId
    ) {
        SettlementDTO settlement =
                settlementMapper.findById(settlementId)
                        .orElseThrow(
                                SettlementErrorCode
                                        .SETTLEMENT_NOT_FOUND
                                        ::toException
                        );

        List<SettlementPaymentObligationResponse> obligations =

                paymentStatusMapper.findPaymentObligationsBySettlementId(
                settlement.getSettlementId()
        );

        BigDecimal totalExpectedAmount = obligations.stream()
                .map(SettlementPaymentObligationResponse::getExpectedAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalPaidAmount = obligations.stream()
                .map(SettlementPaymentObligationResponse::getPaidAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalRemainingAmount = obligations.stream()
                .map(SettlementPaymentObligationResponse::getRemainingAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal progressRate =
                calculateProgressRate(
                        totalExpectedAmount,
                        totalPaidAmount
                );

        boolean closable = !obligations.isEmpty()
                && obligations.stream()
                .allMatch(obligation ->
                        obligation.getRemainingAmount()
                                .compareTo(BigDecimal.ZERO) == 0
                );

        return SettlementPaymentStatusResponse.builder()
                .settlementId(settlement.getSettlementId())
                .obligations(obligations)
                .totalExpectedAmount(totalExpectedAmount)
                .totalPaidAmount(totalPaidAmount)
                .totalRemainingAmount(totalRemainingAmount)
                .progressRate(progressRate)
                .closable(closable)
                .build();
    }

    private BigDecimal calculateProgressRate(
            BigDecimal totalExpectedAmount,
            BigDecimal totalPaidAmount
    ) {
        if (totalExpectedAmount.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }

        return totalPaidAmount
                .multiply(HUNDRED)
                .divide(
                        totalExpectedAmount,
                        RATE_SCALE,
                        RoundingMode.HALF_UP
                );
    }
}


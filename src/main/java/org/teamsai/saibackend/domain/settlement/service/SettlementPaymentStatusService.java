package org.teamsai.saibackend.domain.settlement.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.teamsai.saibackend.domain.payment.type.ObligationStatus;
import org.teamsai.saibackend.domain.settlement.dto.SettlementDTO;
import org.teamsai.saibackend.domain.settlement.dto.response.SettlementObligationStatusResponse;
import org.teamsai.saibackend.domain.settlement.dto.response.SettlementPaymentObligationResponse;
import org.teamsai.saibackend.domain.settlement.dto.response.SettlementPaymentStatusResponse;
import org.teamsai.saibackend.domain.settlement.exception.SettlementErrorCode;
import org.teamsai.saibackend.domain.settlement.mapper.SettlementMapper;
import org.teamsai.saibackend.domain.settlement.mapper.SettlementPaymentStatusMapper;
import org.teamsai.saibackend.domain.payment.type.PaymentStatus;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SettlementPaymentStatusService {

    private static final BigDecimal HUNDRED =
            BigDecimal.valueOf(100);
    private static final int RATE_SCALE = 2;

    private final SettlementMapper settlementMapper;
    private final SettlementPaymentStatusMapper paymentStatusMapper;
    private final SettlementValidator settlementValidator;

    @Transactional(readOnly = true)
    public SettlementPaymentStatusResponse getPaymentStatus(
            Long settlementId,
            Long userId
    ) {
        SettlementDTO settlement =
                settlementMapper.findById(settlementId)
                        .orElseThrow(
                                SettlementErrorCode
                                        .SETTLEMENT_NOT_FOUND
                                        ::toException
                        );
        settlementValidator.validateAccessibleUser(settlement,userId);
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

        BigDecimal totalResolvedAmount = obligations.stream()
                .map(o -> o.getObligationStatus() == ObligationStatus.WRITTEN_OFF
                        ? o.getExpectedAmount()  // 상각분은 기대액 전체를 "해결됨"으로 카운트
                        : o.getPaidAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal progressRate =
                calculateProgressRate(
                        totalExpectedAmount,
                        totalResolvedAmount
                );
        if(progressRate.compareTo(HUNDRED) > 0){
            progressRate = HUNDRED;
        }
        boolean closable = isFullyResolved(obligations);

        Map<PaymentStatus, Long> countByStatus = obligations.stream()
                .collect(Collectors.groupingBy(
                        SettlementPaymentObligationResponse::getPaymentStatus,
                        Collectors.counting()
                ));

        return SettlementPaymentStatusResponse.builder()
                .settlementId(settlement.getSettlementId())
                .obligations(obligations)
                .totalExpectedAmount(totalExpectedAmount)
                .totalPaidAmount(totalPaidAmount)
                .totalRemainingAmount(totalRemainingAmount)
                .paidCount(countByStatus.getOrDefault(PaymentStatus.PAID, 0L))
                .partiallyPaidCount(countByStatus.getOrDefault(PaymentStatus.PARTIALLY_PAID, 0L))
                .unpaidCount(countByStatus.getOrDefault(PaymentStatus.UNPAID, 0L))
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

    @Transactional(readOnly = true)
    public boolean areAllObligationsResolved(Long settlementId) {
        List<SettlementObligationStatusResponse> obligations =
                paymentStatusMapper.findAllObligationStatusesBySettlementId(settlementId);

        return !obligations.isEmpty()
                && obligations.stream().allMatch(o ->
                isResolved(o.obligationStatus())
                        || o.paidAmount().compareTo(o.expectedAmount()) >= 0
        );
    }

    private boolean isResolved(ObligationStatus status) {
        return status == ObligationStatus.WRITTEN_OFF
                || status == ObligationStatus.EXCLUDED
                || status == ObligationStatus.CANCELLED;
    }

    private boolean isFullyResolved(List<SettlementPaymentObligationResponse> obligations) {
        return !obligations.isEmpty()
                && obligations.stream().allMatch(o ->
                o.getObligationStatus() == ObligationStatus.WRITTEN_OFF
                        || o.getPaidAmount().compareTo(o.getExpectedAmount()) == 0
        );
    }
}


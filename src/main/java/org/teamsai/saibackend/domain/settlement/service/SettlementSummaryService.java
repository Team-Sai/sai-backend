package org.teamsai.saibackend.domain.settlement.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.teamsai.saibackend.domain.settlement.dto.response.SettlementListResponse;
import org.teamsai.saibackend.domain.settlement.dto.response.SettlementPaymentObligationResponse;
import org.teamsai.saibackend.domain.settlement.dto.response.SettlementPaymentStatusResponse;
import org.teamsai.saibackend.domain.settlement.dto.response.SettlementSummaryResponse;
import org.teamsai.saibackend.domain.settlement.type.SettlementStatus;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SettlementSummaryService {

    private final SettlementQueryService settlementQueryService;
    private final SettlementPaymentStatusService settlementPaymentStatusService;

    @Transactional(readOnly = true)
    public SettlementSummaryResponse getSummary(Long userId) {
        List<SettlementListResponse> settlements =
                settlementQueryService.getSettlementList(userId);

        BigDecimal receivableAmount = BigDecimal.ZERO;
        BigDecimal payableAmount = BigDecimal.ZERO;

        long receivableCount = 0;
        long payableCount = 0;

        for (SettlementListResponse settlement : settlements) {
            if (!isInProgress(settlement)) {
                continue;
            }

            SettlementPaymentStatusResponse paymentStatus =
                    settlementPaymentStatusService.getPaymentStatus(
                            settlement.settlementId(),
                            userId
                    );

            BigDecimal receivableRemainingAmount =
                    getReceivableRemainingAmount(settlement, paymentStatus);
            if (hasRemainingAmount(receivableRemainingAmount)) {
                receivableAmount = receivableAmount.add(receivableRemainingAmount);
                receivableCount++;
            }

            BigDecimal payableRemainingAmount =
                    getPayableRemainingAmount(settlement, paymentStatus, userId);
            if (hasRemainingAmount(payableRemainingAmount)) {
                payableAmount = payableAmount.add(payableRemainingAmount);
                payableCount++;
            }
        }
        return new SettlementSummaryResponse(
                receivableAmount,
                receivableCount,
                payableAmount,
                payableCount
        );
    }

    private BigDecimal getReceivableRemainingAmount(
            SettlementListResponse settlement,
            SettlementPaymentStatusResponse paymentStatus
    ) {
        if (!isOwner(settlement)) {
            return BigDecimal.ZERO;
        }

        return zeroIfNull(paymentStatus.getTotalRemainingAmount());
    }

    private BigDecimal getPayableRemainingAmount(
            SettlementListResponse settlement,
            SettlementPaymentStatusResponse paymentStatus,
            Long userId
    ) {
        if (!isMember(settlement)) {
            return BigDecimal.ZERO;
        }

        return getMyRemainingAmount(paymentStatus.getObligations(), userId);
    }

    private BigDecimal getMyRemainingAmount(
            List<SettlementPaymentObligationResponse> obligations,
            Long userId
    ) {
        if (obligations == null) {
            return BigDecimal.ZERO;
        }

        return obligations.stream()
                .filter(obligation ->
                        userId.equals(obligation.getUserId())
                )
                .map(
                        SettlementPaymentObligationResponse
                                ::getRemainingAmount
                )
                .map(this::zeroIfNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private boolean isInProgress(
            SettlementListResponse settlement
    ) {
        return SettlementStatus.IN_PROGRESS.name()
                .equals(settlement.settlementStatus());
    }

    private boolean isOwner(
            SettlementListResponse settlement
    ) {
        return "OWNER".equals(settlement.role());
    }

    private boolean isMember(
            SettlementListResponse settlement
    ) {
        return "MEMBER".equals(settlement.role());
    }

    private boolean hasRemainingAmount(BigDecimal amount) {
        return amount.signum() > 0;
    }

    private BigDecimal zeroIfNull(BigDecimal amount) {
        return amount == null
                ? BigDecimal.ZERO
                : amount;
    }
}

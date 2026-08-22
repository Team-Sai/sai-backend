package org.teamsai.saibackend.domain.settlement.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.teamsai.saibackend.domain.settlement.dto.SettlementDTO;
import org.teamsai.saibackend.domain.settlement.dto.response.SettlementCloseResponse;
import org.teamsai.saibackend.domain.settlement.dto.response.SettlementObligationStatusResponse;
import org.teamsai.saibackend.domain.settlement.exception.SettlementErrorCode;
import org.teamsai.saibackend.domain.settlement.mapper.SettlementMapper;
import org.teamsai.saibackend.domain.settlement.mapper.SettlementPaymentStatusMapper;
import org.teamsai.saibackend.domain.settlement.type.SettlementStatus;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SettlementCloseService {
    private final SettlementMapper settlementMapper;
    private final SettlementPaymentStatusService paymentStatusService;
    private final SettlementValidator settlementValidator;
    private final SettlementPaymentStatusMapper paymentStatusMapper;

    @Transactional
    public SettlementCloseResponse close(Long settlementId, Long userId){
        SettlementDTO settlement =
                settlementMapper.findByIdForUpdate(settlementId)
                        .orElseThrow(
                                SettlementErrorCode
                                        .SETTLEMENT_NOT_FOUND
                                        ::toException
                        );
        settlementValidator.validateOwner(settlement,userId);
        if(settlement.getSettlementStatus()
                != SettlementStatus.IN_PROGRESS){
            throw SettlementErrorCode
                    .ALREADY_CLOSED_SETTLEMENT
                    .toException();
        }
        if(!paymentStatusService.areAllObligationsResolved(settlementId)){  // areAllObligationsPaid → areAllObligationsResolved
            throw SettlementErrorCode.SETTLEMENT_NOT_CLOSABLE.toException();
        }
        LocalDateTime closedAt = LocalDateTime.now();
        int updatedCount = settlementMapper.closeSettlement(
                settlementId,
                closedAt
        );
        if(updatedCount != 1){
            throw SettlementErrorCode
                    .SETTLEMENT_CLOSE_FAILED
                    .toException();
        }
        return SettlementCloseResponse.builder()
                .settlementId(settlementId)
                .settlementStatus(SettlementStatus.CLOSED)
                .closedAt(closedAt)
                .build();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean autoCloseIfAllResolved(Long settlementId) {
        List<SettlementObligationStatusResponse> obligations = paymentStatusMapper.findAllObligationStatusesBySettlementId(settlementId);
        log.info("자동종결 체크 settlementId={}, obligations={}", settlementId, obligations);
        SettlementDTO settlement = settlementMapper.findByIdForUpdate(settlementId)
                .orElseThrow(SettlementErrorCode.SETTLEMENT_NOT_FOUND::toException);
        if (settlement.getSettlementStatus() != SettlementStatus.IN_PROGRESS) {
            return false;
        }
        if (!paymentStatusService.areAllObligationsResolved(settlementId)) {
            return false;
        }
        LocalDateTime closedAt = LocalDateTime.now();
        int updatedCount = settlementMapper.closeSettlement(settlementId, closedAt);
        return updatedCount == 1;
    }
}
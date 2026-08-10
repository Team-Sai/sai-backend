package org.teamsai.saibackend.domain.settlement.service;


import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.teamsai.saibackend.domain.settlement.dto.SettlementDTO;
import org.teamsai.saibackend.domain.settlement.dto.response.SettlementCloseResponse;
import org.teamsai.saibackend.domain.settlement.dto.response.SettlementPaymentStatusResponse;
import org.teamsai.saibackend.domain.settlement.exception.SettlementErrorCode;
import org.teamsai.saibackend.domain.settlement.mapper.SettlementMapper;
import org.teamsai.saibackend.domain.settlement.type.SettlementStatus;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class SettlementCloseService {

    private final SettlementMapper settlementMapper;
    private final SettlementPaymentStatusService paymentStatusService;

    @Transactional
    public SettlementCloseResponse close(
            Long settlementId,
            Long userId
    ){
        SettlementDTO settlement =
                settlementMapper.findByIdForUpdate(settlementId)
                        .orElseThrow(
                                SettlementErrorCode
                                        .SETTLEMENT_NOT_FOUND
                                        ::toException
                        );
        if(!settlement.getOwnerId().equals(userId)){
            throw SettlementErrorCode
                    .SETTLEMENT_ACCESS_DENIED
                    .toException();
        }

        if(settlement.getSettlementStatus()
            != SettlementStatus.IN_PROGRESS){
            throw SettlementErrorCode
                    .ALREADY_CLOSED_SETTLEMENT
                    .toException();
        }

        SettlementPaymentStatusResponse paymentStatus =
                paymentStatusService.getPaymentStatus(
                        settlementId,
                        settlement.getOwnerId()
                );

        if(!paymentStatus.isClosable()){
            throw SettlementErrorCode
                    .SETTLEMENT_NOT_CLOSABLE
                    .toException();
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
}

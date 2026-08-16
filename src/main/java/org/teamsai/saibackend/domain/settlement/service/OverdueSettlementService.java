package org.teamsai.saibackend.domain.settlement.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.teamsai.saibackend.domain.payment.dto.PaymentObligationDTO;
import org.teamsai.saibackend.domain.payment.mapper.PaymentObligationMapper;
import org.teamsai.saibackend.domain.settlement.dto.SettlementDTO;
import org.teamsai.saibackend.domain.settlement.dto.SettlementParticipantDTO;
import org.teamsai.saibackend.domain.settlement.mapper.SettlementMapper;
import org.teamsai.saibackend.domain.settlement.mapper.SettlementParticipantMapper;
import org.teamsai.saibackend.domain.settlement.type.SettlementParticipantStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class OverdueSettlementService {
    private final OverdueCriteria overdueCriteria;
    private final SettlementMapper settlementMapper;
    private final SettlementParticipantMapper participantMapper;
    private final PaymentObligationMapper paymentObligationMapper;

    @Transactional
    public void updateOverdueStatus(LocalDate baseDate){
        List<SettlementDTO> inProgressSettlements = settlementMapper.findInProgressSettlements();

        for(SettlementDTO settlement : inProgressSettlements){
            if(!overdueCriteria.isOverdue(settlement,baseDate)){
                continue;
            }
            markOverdueForSettlement(settlement,baseDate);
        }
    }

    private void markOverdueForSettlement(SettlementDTO settlement, LocalDate baseDate){
        List<Long> activeParticipantIds = participantMapper.findBySettlementId(settlement.getSettlementId())
                .stream()
                .filter(p -> p.getParticipantStatus() == SettlementParticipantStatus.ACTIVE)
                .map(SettlementParticipantDTO::getParticipantId)
                .toList();

        if(activeParticipantIds.isEmpty()){
            return;
        }

        List<PaymentObligationDTO> unpaidObligations = paymentObligationMapper.findUnpaidByParticipantIds(activeParticipantIds);

        LocalDateTime overdueSince = baseDate.atStartOfDay();

        for(PaymentObligationDTO obligation : unpaidObligations){
            paymentObligationMapper.updateOverdueSince(obligation.getPaymentObligationId(), overdueSince);
        }
    }
}

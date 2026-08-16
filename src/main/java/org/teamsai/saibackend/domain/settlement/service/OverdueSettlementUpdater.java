package org.teamsai.saibackend.domain.settlement.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.teamsai.saibackend.domain.payment.dto.PaymentObligationDTO;
import org.teamsai.saibackend.domain.payment.mapper.PaymentObligationMapper;
import org.teamsai.saibackend.domain.settlement.dto.SettlementDTO;
import org.teamsai.saibackend.domain.settlement.dto.SettlementParticipantDTO;
import org.teamsai.saibackend.domain.settlement.mapper.SettlementParticipantMapper;
import org.teamsai.saibackend.domain.settlement.type.SettlementParticipantStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class OverdueSettlementUpdater {

    private final SettlementParticipantMapper participantMapper;
    private final PaymentObligationMapper paymentObligationMapper;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateOverdueForSettlement(SettlementDTO settlement, LocalDate baseDate) {
        List<Long> activeParticipantIds = participantMapper.findBySettlementId(settlement.getSettlementId())
                .stream()
                .filter(p -> p.getParticipantStatus() == SettlementParticipantStatus.ACTIVE)
                .map(SettlementParticipantDTO::getParticipantId)
                .toList();

        if (activeParticipantIds.isEmpty()) {
            return;
        }

        List<PaymentObligationDTO> unpaidObligations =
                paymentObligationMapper.findUnpaidByParticipantIds(activeParticipantIds);

        LocalDateTime overdueSince = baseDate.atStartOfDay();

        for (PaymentObligationDTO obligation : unpaidObligations) {
            paymentObligationMapper.updateOverdueSince(obligation.getPaymentObligationId(), overdueSince);
        }
    }
}
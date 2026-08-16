package org.teamsai.saibackend.domain.settlement.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

@Slf4j
@Service
@RequiredArgsConstructor
public class OverdueSettlementUpdater {

    private final OverdueCriteria overdueCriteria;
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

        if (unpaidObligations.isEmpty()) {
            return;
        }


        LocalDate referenceDate = overdueCriteria.resolveReferenceDate(settlement);
        LocalDateTime overdueSince = referenceDate.atStartOfDay();

        for (PaymentObligationDTO obligation : unpaidObligations) {
            int updatedCount = paymentObligationMapper.updateOverdueSince(
                    obligation.getPaymentObligationId(), overdueSince);
            if (updatedCount == 0) {
                log.info("연체 처리 스킵 (이미 완납 등으로 조건 불일치) paymentObligationId={}",
                        obligation.getPaymentObligationId());
            }
        }
    }
}

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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class OverdueSettlementUpdater {

    private final SettlementParticipantMapper participantMapper;
    private final PaymentObligationMapper paymentObligationMapper;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateOverdueForSettlement(SettlementDTO settlement, LocalDate referenceDate) {
        List<Long> activeParticipantIds = participantMapper.
                findActiveBySettlementId(settlement.getSettlementId())
                .stream()
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

        LocalDateTime overdueSince = referenceDate.plusDays(1).atStartOfDay();
        List<Long> obligationIds = unpaidObligations.stream()
                .map(PaymentObligationDTO::getPaymentObligationId)
                .toList();

        int updatedCount = paymentObligationMapper.updateOverdueSinceBulk(obligationIds, overdueSince);

        if (updatedCount < obligationIds.size()) {
            log.info("일부 연체 처리 스킵됨 (이미 완납 등으로 조건 불일치) settlementId={}, 대상={}건, 실제갱신={}건",
                    settlement.getSettlementId(), obligationIds.size(), updatedCount);
        }
    }
}
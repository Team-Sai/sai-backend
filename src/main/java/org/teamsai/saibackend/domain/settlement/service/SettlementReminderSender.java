package org.teamsai.saibackend.domain.settlement.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.teamsai.saibackend.domain.notification.service.NotificationService;
import org.teamsai.saibackend.domain.notification.type.ReminderStage;
import org.teamsai.saibackend.domain.payment.dto.PaymentObligationDTO;
import org.teamsai.saibackend.domain.payment.mapper.PaymentObligationMapper;
import org.teamsai.saibackend.domain.settlement.dto.SettlementDTO;
import org.teamsai.saibackend.domain.settlement.dto.SettlementParticipantDTO;
import org.teamsai.saibackend.domain.settlement.mapper.SettlementParticipantMapper;
import org.teamsai.saibackend.domain.settlement.type.SettlementParticipantStatus;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class SettlementReminderSender {

    private final SettlementParticipantMapper participantMapper;
    private final PaymentObligationMapper paymentObligationMapper;
    private final NotificationService notificationService;

    public int sendForSettlement(SettlementDTO settlement, ReminderStage stage) {
        List<SettlementParticipantDTO> activeParticipants =
                participantMapper.findBySettlementId(settlement.getSettlementId())
                        .stream()
                        .filter(p -> p.getParticipantStatus() == SettlementParticipantStatus.ACTIVE)
                        .toList();

        if (activeParticipants.isEmpty()) {
            return 0;
        }

        Map<Long, Long> userIdByParticipantId = activeParticipants.stream()
                .collect(Collectors.toMap(
                        SettlementParticipantDTO::getParticipantId,
                        SettlementParticipantDTO::getUserId
                ));

        List<Long> participantIds = activeParticipants.stream()
                .map(SettlementParticipantDTO::getParticipantId)
                .toList();

        List<PaymentObligationDTO> unresolvedObligations =
                paymentObligationMapper.findByParticipantIds(participantIds).stream()
                        .filter(o -> o.getPaymentStatus().isUnresolved())
                        .toList();

        int sent = 0;
        for (PaymentObligationDTO obligation : unresolvedObligations) {
            Long userId = userIdByParticipantId.get(obligation.getParticipantId());
            if (userId == null) {
                log.warn("참여자-사용자 매핑 실패 participantId={}", obligation.getParticipantId());
                continue;
            }
            try {
                sendOneReminder(userId, obligation, settlement, stage);
                sent++;
            } catch (Exception e) {
                log.error("리마인드 발송 실패 paymentObligationId={}", obligation.getPaymentObligationId(), e);
            }
        }
        return sent;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void sendOneReminder(Long userId, PaymentObligationDTO obligation, SettlementDTO settlement, ReminderStage stage) {
        notificationService.createIfAbsent(
                userId,
                stage.type(),
                stage.title(),
                stage.contentFor(settlement, obligation),
                obligation.getPaymentObligationId(),  // referenceId — obligation 단위로 dedup
                settlement.getSettlementId()           // secondaryReferenceId — 참고용
        );
    }
}
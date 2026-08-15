package org.teamsai.saibackend.domain.settlement.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.teamsai.saibackend.domain.payment.dto.PaymentObligationDTO;
import org.teamsai.saibackend.domain.payment.exception.PaymentErrorCode;
import org.teamsai.saibackend.domain.payment.mapper.PaymentObligationMapper;
import org.teamsai.saibackend.domain.payment.service.SettlementPaymentService;
import org.teamsai.saibackend.domain.settlement.dto.RecurringSettlementDTO;
import org.teamsai.saibackend.domain.settlement.dto.SettlementDTO;
import org.teamsai.saibackend.domain.settlement.dto.SettlementParticipantDTO;
import org.teamsai.saibackend.domain.settlement.exception.SettlementErrorCode;
import org.teamsai.saibackend.domain.settlement.mapper.SettlementMapper;
import org.teamsai.saibackend.domain.settlement.mapper.SettlementParticipantMapper;
import org.teamsai.saibackend.domain.settlement.type.SettlementParticipantStatus;
import org.teamsai.saibackend.domain.settlement.type.SettlementStatus;
import org.teamsai.saibackend.domain.settlement.type.SettlementType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class RecurringSettlementCycleGenerator {
    private final SettlementMapper settlementMapper;
    private final SettlementParticipantMapper participantMapper;
    private final PaymentObligationMapper paymentObligationMapper;
    private final SettlementPaymentService settlementPaymentService; // 기존 서비스 재사용


    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void generateNextCycle(RecurringSettlementDTO recurring, LocalDate baseDate) {
        SettlementDTO latestSettlement =
                settlementMapper.findLatestByRecurringId(recurring.getRecurringSettlementId());

        if (latestSettlement == null) {
            log.warn("직전 회차 없음, 생성 스킵 recurringId={}", recurring.getRecurringSettlementId());
            return;
        }

        if (!isDueToday(recurring, latestSettlement, baseDate)) {
            return;
        }

        List<SettlementParticipantDTO> activeParticipants =
                participantMapper.findBySettlementId(latestSettlement.getSettlementId())
                        .stream()
                        .filter(p -> p.getParticipantStatus() == SettlementParticipantStatus.ACTIVE)
                        .toList();

        if (activeParticipants.isEmpty()) {
            log.warn("ACTIVE 참여자 없음, 생성 스킵 recurringId={}", recurring.getRecurringSettlementId());
            return;
        }

        SettlementDTO newSettlement = SettlementDTO.builder()
                .recurringSettlementId(recurring.getRecurringSettlementId())
                .ownerId(recurring.getOwnerId())
                .settlementType(SettlementType.RECURRING)
                .settlementStatus(SettlementStatus.IN_PROGRESS)
                .settlementCategory(recurring.getSettlementCategory())
                .title(recurring.getTitle())
                .splitType(recurring.getSplitType())
                .totalAmount(recurring.getTotalAmount())
                .cycleDate(baseDate)
                .dueDate(null)
                .createdAt(LocalDateTime.now())
                .build();

        int inserted = settlementMapper.insertSettlement(newSettlement);
        if (inserted != 1) {
            throw SettlementErrorCode.SETTLEMENT_CREATE_FAILED.toException();
        }

        for (SettlementParticipantDTO oldParticipant : activeParticipants) {
            copyParticipantWithObligation(oldParticipant, newSettlement.getSettlementId());
        }
    }

    private boolean isDueToday(RecurringSettlementDTO recurring, SettlementDTO latestSettlement, LocalDate baseDate) {
        LocalDate lastCycleDate = latestSettlement.getCycleDate();

        return switch (recurring.getCycleRule()) {
            case DAILY -> !lastCycleDate.isEqual(baseDate);
            case WEEKLY -> ChronoUnit.WEEKS.between(lastCycleDate, baseDate) >= 1;
            case MONTHLY -> ChronoUnit.MONTHS.between(lastCycleDate, baseDate) >= 1
                    && lastCycleDate.getDayOfMonth() == baseDate.getDayOfMonth();
            case YEARLY -> ChronoUnit.YEARS.between(lastCycleDate, baseDate) >= 1
                    && lastCycleDate.getMonth() == baseDate.getMonth()
                    && lastCycleDate.getDayOfMonth() == baseDate.getDayOfMonth();
        };
    }

    private void copyParticipantWithObligation(SettlementParticipantDTO oldParticipant, Long newSettlementId) {
        SettlementParticipantDTO newParticipant = SettlementParticipantDTO.builder()
                .userId(oldParticipant.getUserId())
                .settlementId(newSettlementId)
                .participantRole(oldParticipant.getParticipantRole())
                .participantStatus(SettlementParticipantStatus.ACTIVE)
                .joinedAt(LocalDateTime.now())
                .build();

        int inserted = participantMapper.insert(newParticipant);
        if (inserted != 1) {
            throw SettlementErrorCode.SETTLEMENT_PARTICIPANT_CREATE_FAILED.toException();
        }

        BigDecimal expectedAmount = paymentObligationMapper.findByParticipantId(oldParticipant.getParticipantId())
                .map(PaymentObligationDTO::getExpectedAmount)
                .orElseThrow(PaymentErrorCode.PAYMENT_OBLIGATION_NOT_FOUND::toException);
        
        settlementPaymentService.createObligation(newParticipant.getParticipantId(), expectedAmount);
    }
}

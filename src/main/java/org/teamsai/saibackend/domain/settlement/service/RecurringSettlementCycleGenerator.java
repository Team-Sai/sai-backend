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
import org.teamsai.saibackend.domain.settlement.type.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class RecurringSettlementCycleGenerator {

    private final SettlementMapper settlementMapper;
    private final SettlementParticipantMapper participantMapper;
    private final PaymentObligationMapper paymentObligationMapper;
    private final SettlementPaymentService settlementPaymentService;
    private final SettlementAmountCalculator settlementAmountCalculator;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public CycleGenerationOutcome generateOneCycle(RecurringSettlementDTO recurring, SettlementDTO previousSettlement, LocalDate cycleDate) {
        SettlementDTO lockedLatest =
                settlementMapper.findLatestByRecurringIdForUpdate(recurring.getRecurringSettlementId());

        if (lockedLatest == null || !lockedLatest.getSettlementId().equals(previousSettlement.getSettlementId())) {
            log.warn("동시 생성 감지, 스킵 recurringId={}", recurring.getRecurringSettlementId());
            return CycleGenerationOutcome.concurrentlySkipped();
        }

        List<SettlementParticipantDTO> activeParticipants =
                participantMapper.findActiveBySettlementId(previousSettlement.getSettlementId());

        if (activeParticipants.isEmpty()) {
            log.warn("ACTIVE 참여자 없음, 생성 스킵 recurringId={}, cycleDate={}",
                    recurring.getRecurringSettlementId(), cycleDate);
            return CycleGenerationOutcome.noActiveParticipant();
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
                .cycleDate(cycleDate)
                .dueDate(null)
                .createdAt(LocalDateTime.now())
                .build();

        int inserted = settlementMapper.insertSettlement(newSettlement);
        if (inserted != 1) {
            throw SettlementErrorCode.SETTLEMENT_CREATE_FAILED.toException();
        }

        if (recurring.getSplitType() == SplitType.EQUAL) {
            copyParticipantsWithEqualSplit(activeParticipants, newSettlement, recurring.getTotalAmount());
        } else {
            copyParticipantsWithCustomAmounts(activeParticipants, newSettlement);
        }

        return CycleGenerationOutcome.created(newSettlement);
    }

    private void copyParticipantsWithEqualSplit(
            List<SettlementParticipantDTO> activeParticipants, SettlementDTO newSettlement, BigDecimal totalAmount
    ) {
        BigDecimal perPersonAmount =
                settlementAmountCalculator.calculateEqualAmount(
                        totalAmount,
                        activeParticipants.size()
                );

        for (SettlementParticipantDTO oldParticipant : activeParticipants) {
            copyParticipantWithObligation(
                    oldParticipant,
                    newSettlement.getSettlementId(),
                    perPersonAmount
            );
        }
    }

    private void copyParticipantsWithCustomAmounts(
            List<SettlementParticipantDTO> activeParticipants, SettlementDTO newSettlement
    ) {
        List<Long> participantIds = activeParticipants.stream()
                .map(SettlementParticipantDTO::getParticipantId)
                .toList();

        Map<Long, BigDecimal> latestObligationByParticipant = paymentObligationMapper
                .findLatestByParticipantIdsIncludingWrittenOff(participantIds)
                .stream()
                .collect(Collectors.toMap(PaymentObligationDTO::getParticipantId, PaymentObligationDTO::getExpectedAmount));
        
        for (SettlementParticipantDTO oldParticipant : activeParticipants) {
            BigDecimal expectedAmount = latestObligationByParticipant.get(oldParticipant.getParticipantId());
            if (expectedAmount == null) {
                throw PaymentErrorCode.PAYMENT_OBLIGATION_NOT_FOUND.toException();
            }
            copyParticipantWithObligation(oldParticipant, newSettlement.getSettlementId(), expectedAmount);
        }
    }

    private void copyParticipantWithObligation(
            SettlementParticipantDTO oldParticipant, Long newSettlementId, BigDecimal expectedAmount
    ) {
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

        settlementPaymentService.createObligation(newParticipant.getParticipantId(), expectedAmount);
    }

    public LocalDate calculateNthCycleDate(LocalDate startDate, CycleRule cycleRule, int n) {
        return switch (cycleRule) {
            case DAILY -> startDate.plusDays(n);
            case WEEKLY -> startDate.plusWeeks(n);
            case MONTHLY -> clampToMonth(YearMonth.from(startDate).plusMonths(n), startDate.getDayOfMonth());
            case YEARLY -> clampToMonth(YearMonth.of(startDate.getYear() + n, startDate.getMonthValue()), startDate.getDayOfMonth());
        };
    }

    private LocalDate clampToMonth(YearMonth targetMonth, int anchorDay) {
        int actualDay = Math.min(anchorDay, targetMonth.lengthOfMonth());
        return targetMonth.atDay(actualDay);
    }
}
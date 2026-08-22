package org.teamsai.saibackend.domain.settlement.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.teamsai.saibackend.domain.batch.common.notification.SlackNotifier;
import org.teamsai.saibackend.domain.payment.dto.PaymentObligationDTO;
import org.teamsai.saibackend.domain.payment.exception.PaymentErrorCode;
import org.teamsai.saibackend.domain.payment.mapper.PaymentObligationMapper;
import org.teamsai.saibackend.domain.payment.service.SettlementPaymentService;
import org.teamsai.saibackend.domain.settlement.dto.RecurringSettlementDTO;
import org.teamsai.saibackend.domain.settlement.dto.SettlementAccountDTO;
import org.teamsai.saibackend.domain.settlement.dto.SettlementDTO;
import org.teamsai.saibackend.domain.settlement.dto.SettlementParticipantDTO;
import org.teamsai.saibackend.domain.settlement.exception.SettlementErrorCode;
import org.teamsai.saibackend.domain.settlement.mapper.SettlementAccountMapper;
import org.teamsai.saibackend.domain.settlement.mapper.SettlementMapper;
import org.teamsai.saibackend.domain.settlement.mapper.SettlementParticipantMapper;
import org.teamsai.saibackend.domain.settlement.type.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
    private final SettlementAccountMapper settlementAccountMapper;
    private final SlackNotifier slackNotifier;

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

        copySettlementAccount(previousSettlement.getSettlementId(), newSettlement.getSettlementId());

        return CycleGenerationOutcome.created(newSettlement);
    }

    private void copySettlementAccount(Long previousSettlementId, Long newSettlementId) {
        Optional<SettlementAccountDTO> previousAccount =
                settlementAccountMapper.findActiveBySettlementId(previousSettlementId);

        if (previousAccount.isEmpty()) {
            log.warn("직전 회차에 연결된 계좌 없음, 계좌 승계 스킵 previousSettlementId={}", previousSettlementId);
            return;
        }

        SettlementAccountDTO newAccount = SettlementAccountDTO.builder()
                .settlementId(newSettlementId)
                .linkedAccountId(previousAccount.get().getLinkedAccountId())
                .accountStatus(SettlementAccountStatus.ACTIVE)
                .selectedAt(LocalDateTime.now())
                .endedAt(null)
                .build();

        int insertedCount = settlementAccountMapper.insert(newAccount);
        if (insertedCount != 1) {
            log.error("정산 계좌 승계 실패 newSettlementId={}", newSettlementId);
            slackNotifier.send(
                    "[정기정산] 계좌 승계 실패 - settlementId=" + newSettlementId
                            + " (이 회차는 은행거래 자동매칭이 되지 않습니다. 수동 확인 필요)"
            );
        }
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
package org.teamsai.saibackend.domain.settlement.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.teamsai.saibackend.domain.notification.type.ReminderStage;
import org.teamsai.saibackend.domain.payment.mapper.PaymentObligationMapper;
import org.teamsai.saibackend.domain.settlement.dto.SettlementDTO;
import org.teamsai.saibackend.domain.settlement.mapper.SettlementMapper;
import org.teamsai.saibackend.domain.settlement.mapper.SettlementParticipantMapper;


import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SettlementDueReminderService {

    private static final int PAGE_SIZE = 100;

    private final OverdueCriteria overdueCriteria;
    private final SettlementMapper settlementMapper;
    private final SettlementParticipantMapper participantMapper;
    private final PaymentObligationMapper paymentObligationMapper;
    private final SettlementReminderSender reminderSender;

    public SettlementReminderResult sendDueReminders(LocalDate baseDate) {
        int totalCount = settlementMapper.countInProgressSettlements();
        log.info("정산 리마인드 배치 시작, 대상 정산 총 {}건, baseDate={}", totalCount, baseDate);

        int processedCount = 0;
        int failedCount = 0;
        int offset = 0;

        while (true) {
            List<SettlementDTO> page = settlementMapper.findInProgressSettlements(offset, PAGE_SIZE);
            if (page.isEmpty()) {
                break;
            }

            for (SettlementDTO settlement : page) {
                LocalDate referenceDate = overdueCriteria.resolveReferenceDate(settlement);
                ReminderStage stage = resolveStage(referenceDate, baseDate);
                if (stage == null) {
                    continue;
                }
                try {
                    int sent = reminderSender.sendForSettlement(settlement, stage);
                    processedCount += sent;
                } catch (Exception e) {
                    failedCount++;
                    log.error("정산 리마인드 발송 실패 settlementId={}", settlement.getSettlementId(), e);
                }
            }

            if (page.size() < PAGE_SIZE) {
                break;
            }
            offset += PAGE_SIZE;
        }

        log.info("정산 리마인드 배치 종료, 발송 {}건 / 실패(정산 단위) {}건", processedCount, failedCount);
        return new SettlementReminderResult(processedCount, failedCount);
    }

    private ReminderStage resolveStage(LocalDate referenceDate, LocalDate baseDate) {
        if (referenceDate == null) {
            return null;
        }
        long daysUntil = ChronoUnit.DAYS.between(baseDate, referenceDate);
        return switch ((int) daysUntil) {
            case 3 -> ReminderStage.D3;
            case 1 -> ReminderStage.D1;
            case 0 -> ReminderStage.DDAY;
            default -> null;
        };
    }
}
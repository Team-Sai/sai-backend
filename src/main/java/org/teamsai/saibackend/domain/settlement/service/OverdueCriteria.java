package org.teamsai.saibackend.domain.settlement.service;

import org.springframework.stereotype.Component;
import org.teamsai.saibackend.domain.settlement.dto.SettlementDTO;
import org.teamsai.saibackend.domain.settlement.type.SettlementStatus;

import java.time.LocalDate;

@Component
public class OverdueCriteria {
    public boolean isOverdue(SettlementDTO settlement, LocalDate baseDate, LocalDate referenceDate) {
        if (settlement.getSettlementStatus() != SettlementStatus.IN_PROGRESS) {
            return false;
        }
        return referenceDate != null && referenceDate.isBefore(baseDate);
    }

    public LocalDate resolveReferenceDate(SettlementDTO settlement) {
        return switch (settlement.getSettlementType()) {
            case SHARED -> settlement.getDueDate();
            case RECURRING -> settlement.getCycleDate();
        };
    }
}

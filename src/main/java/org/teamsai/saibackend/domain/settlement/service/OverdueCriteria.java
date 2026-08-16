package org.teamsai.saibackend.domain.settlement.service;

import org.springframework.cglib.core.Local;
import org.springframework.stereotype.Component;
import org.teamsai.saibackend.domain.settlement.dto.SettlementDTO;

import java.time.LocalDate;

@Component
public class OverdueCriteria {
    public boolean isOverdue(SettlementDTO settlement, LocalDate baseDate){
        LocalDate referenceDate = resolveReferenceDate(settlement);
        return referenceDate != null && referenceDate.isBefore(baseDate);
    }

    private LocalDate resolveReferenceDate(SettlementDTO settlement){
        return switch (settlement.getSettlementType()){
            case SHARED -> settlement.getDueDate();
            case RECURRING -> settlement.getCycleDate();
        };
    }
}

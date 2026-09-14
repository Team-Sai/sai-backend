package org.teamsai.saibackend.domain.settlement.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.teamsai.saibackend.domain.settlement.dto.RecurringSettlementDTO;

@Mapper
public interface RecurringSettlementManagementMapper {
    int insert(RecurringSettlementDTO recurringSettlement);
}

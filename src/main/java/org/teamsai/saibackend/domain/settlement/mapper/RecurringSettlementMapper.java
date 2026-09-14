package org.teamsai.saibackend.domain.settlement.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.teamsai.saibackend.domain.settlement.dto.RecurringSettlementDTO;

import java.time.LocalDate;
import java.util.List;

@Mapper
public interface RecurringSettlementMapper {
    List<RecurringSettlementDTO> findActiveInRange(@Param("baseDate") LocalDate baseDate);

}

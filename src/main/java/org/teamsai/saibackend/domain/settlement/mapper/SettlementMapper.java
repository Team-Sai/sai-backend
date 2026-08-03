package org.teamsai.saibackend.domain.settlement.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.teamsai.saibackend.domain.settlement.dto.SettlementDTO;

@Mapper
public interface SettlementMapper {
    int insertSettlement(SettlementDTO settlement);
}

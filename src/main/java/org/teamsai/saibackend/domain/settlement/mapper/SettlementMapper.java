package org.teamsai.saibackend.domain.settlement.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.teamsai.saibackend.domain.settlement.dto.SettlementDTO;

import java.util.Optional;

@Mapper
public interface SettlementMapper {
    int insertSettlement(SettlementDTO settlement);

    Optional<SettlementDTO> findById(
            @Param("settlementId") Long settlementId
    );
}

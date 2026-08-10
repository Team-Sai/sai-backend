package org.teamsai.saibackend.domain.settlement.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.teamsai.saibackend.domain.settlement.dto.SettlementDTO;
import org.teamsai.saibackend.domain.settlement.dto.response.SettlementListResponse;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Mapper
public interface SettlementMapper {
    int insertSettlement(SettlementDTO settlement);

    Optional<SettlementDTO> findById(
            @Param("settlementId") Long settlementId
    );

    Optional<SettlementDTO> findByIdForUpdate(
            @Param("settlementId") Long settlementId
    );

    int closeSettlement(
            @Param("settlementId") Long settlementId,
            @Param("closedAt") LocalDateTime closedAt
    );

    List<SettlementListResponse> findAllByUserId(Long userId);

}

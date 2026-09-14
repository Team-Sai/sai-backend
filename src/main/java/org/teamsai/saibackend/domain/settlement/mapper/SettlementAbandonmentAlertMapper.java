package org.teamsai.saibackend.domain.settlement.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;

@Mapper
public interface SettlementAbandonmentAlertMapper {

    int insertIfAbsent(
            @Param("settlementId") Long settlementId,
            @Param("referenceDate") LocalDate referenceDate
    );
}
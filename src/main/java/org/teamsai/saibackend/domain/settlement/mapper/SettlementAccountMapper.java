package org.teamsai.saibackend.domain.settlement.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.teamsai.saibackend.domain.settlement.dto.SettlementAccountDTO;
import org.teamsai.saibackend.domain.settlement.type.SettlementAccountStatus;

import java.time.LocalDateTime;
import java.util.Optional;

@Mapper
public interface SettlementAccountMapper {

    int insert(SettlementAccountDTO settlementAccount);

    Optional<SettlementAccountDTO> findActiveBySettlementId(Long settlementId);

    Optional<SettlementAccountDTO> findActiveBySettlementIdForUpdate(Long settlementId);

    int updateStatus(
            @Param("settlementAccountId") Long settlementAccountId,
            @Param("accountStatus")SettlementAccountStatus accountStatus,
            @Param("endedAt")LocalDateTime endedAt
            );
}

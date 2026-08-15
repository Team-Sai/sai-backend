package org.teamsai.saibackend.domain.settlement.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.teamsai.saibackend.domain.settlement.dto.SettlementParticipantDTO;

import java.util.List;

@Mapper
public interface  SettlementParticipantMapper {
    int insert(SettlementParticipantDTO participant);

    List<SettlementParticipantDTO> findBySettlementId(@Param("settlementId") Long settlementId);

    boolean existsActiveParticipant(
            @Param("settlementId") Long settlementId,
            @Param("userId") Long userId
    );
}

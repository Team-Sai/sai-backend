package org.teamsai.saibackend.domain.settlement.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.teamsai.saibackend.domain.settlement.dto.response.SettlementPaymentObligationResponse;

import java.util.List;

@Mapper
public interface SettlementPaymentStatusMapper {

    List<SettlementPaymentObligationResponse>
            findPaymentObligationsBySettlementId(
                    @Param("settlementId") Long settlementId
    );
}

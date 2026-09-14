package org.teamsai.saibackend.domain.settlement.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.teamsai.saibackend.domain.settlement.dto.response.SettlementDetailResponse;
import org.teamsai.saibackend.domain.settlement.dto.response.SettlementListResponse;
import org.teamsai.saibackend.domain.settlement.exception.SettlementErrorCode;
import org.teamsai.saibackend.domain.settlement.mapper.SettlementMapper;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SettlementQueryService {

    private final SettlementMapper settlementMapper;

    @Transactional(readOnly = true)
    public List<SettlementListResponse> getSettlementList(Long userId) {
        return settlementMapper.findAllByUserId(userId);
    }

    @Transactional(readOnly = true)
    public SettlementDetailResponse getSettlementDetail(Long settlementId, Long userId){
        SettlementDetailResponse response = settlementMapper.findDetailById(settlementId,userId)
                .orElseThrow(SettlementErrorCode.SETTLEMENT_NOT_FOUND::toException);

        if("NONE".equals(response.role())){
            throw SettlementErrorCode.SETTLEMENT_ACCESS_DENIED.toException();
        }
        return response;
    }
}

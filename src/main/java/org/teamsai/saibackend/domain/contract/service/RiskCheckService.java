package org.teamsai.saibackend.domain.contract.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.teamsai.saibackend.domain.contract.mapper.RiskCheckMapper;

@Service
@RequiredArgsConstructor
public class RiskCheckService {

    private final RiskCheckMapper riskCheckMapper;

    public Long getPreviousTotalAmount(Long userId) {
        return riskCheckMapper.sumCompletedPrincipalByCreditor(userId);
    }
}

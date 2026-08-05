package org.teamsai.saibackend.domain.contractdetail.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.teamsai.saibackend.domain.contract.dto.response.LoanContractResponse;
import org.teamsai.saibackend.domain.contract.service.contract.LoanContractService;
import org.teamsai.saibackend.domain.contractdetail.dto.response.ContractDetailResponse;

@Service
@RequiredArgsConstructor
public class ContractDetailService {

    private final LoanContractService loanContractService;

    public ContractDetailResponse getCheck(Long contractId, Long userId){
        LoanContractResponse contract = loanContractService.findContract(contractId, userId);

        boolean canRequestChange = contract.getCreditorId().equals(userId);

        return ContractDetailResponse.builder()
                .contract(contract)
                .canRequestChange(canRequestChange)
                .build();
    }
}

package org.teamsai.saibackend.domain.contract.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.teamsai.saibackend.domain.contract.dto.response.LoanContractResponse;
import org.teamsai.saibackend.domain.contract.service.LoanContractService;
import org.teamsai.saibackend.domain.contract.service.ContractChangeService;
import org.teamsai.saibackend.domain.contract.dto.response.ContractDetailResponse;

import java.util.Objects;

@Service
@RequiredArgsConstructor
public class ContractDetailService {

    private final LoanContractService loanContractService;
    private final ContractChangeService contractChangeService;

    public boolean canRequestChange(Long contractId, Long userId) {
        LoanContractResponse contract = loanContractService.findContract(contractId, userId);
        boolean isCreditor = contract.getCreditorId().equals(userId);
        boolean isDebtor = Objects.equals(contract.getDebtorId(), userId);
        return (isCreditor || isDebtor) && !contractChangeService.hasPendingChangeRequest(contractId);
    }


    public ContractDetailResponse getCheck(Long contractId, Long userId){
        LoanContractResponse contract = loanContractService.findContract(contractId, userId);

        boolean isCreditor = contract.getCreditorId().equals(userId);
        boolean isDebtor = Objects.equals(contract.getDebtorId(), userId);
        boolean canRequestChange = (isCreditor || isDebtor) && !contractChangeService.hasPendingChangeRequest(contractId);

        String address = isCreditor
                ? contract.getCreditorAddress()
                : contract.getDebtorAddress();

        return ContractDetailResponse.builder()
                .contract(contract)
                .canRequestChange(canRequestChange)
                .isCreditor(isCreditor)
                .address(address)
                .build();
    }
}

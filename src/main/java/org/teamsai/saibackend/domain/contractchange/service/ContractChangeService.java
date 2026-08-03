package org.teamsai.saibackend.domain.contractchange.service;


import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.teamsai.saibackend.domain.contract.dto.request.ContractStatus;
import org.teamsai.saibackend.domain.contract.dto.response.LoanContractResponse;
import org.teamsai.saibackend.domain.contract.service.contract.LoanContractService;
import org.teamsai.saibackend.domain.contractchange.dto.request.ContractChangeRequest;
import org.teamsai.saibackend.domain.contractchange.dto.LoanContractChangeDTO;
import org.teamsai.saibackend.domain.contractchange.exception.ContractChangeErrorCode;
import org.teamsai.saibackend.domain.contractchange.mapper.ContractChangeMapper;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ContractChangeService {

    private final ContractChangeMapper contractChangeMapper;
    private final LoanContractService loanContractService;

    public LoanContractResponse getContract(Long contractId, Long userID) {
        LoanContractResponse contract = loanContractService.findContract(contractId, userID);

        if(contract.getStatus() != ContractStatus.COMPLETED) {
            throw ContractChangeErrorCode.CONTRACT_NOT_COMPLETED.toException();
        }
        return contract;
    }

    @Transactional
    public LoanContractChangeDTO requestChange(Long contractId, ContractChangeRequest request, Long userId) {



        LoanContractResponse contract = loanContractService.findContract(contractId, userId);

        if(!contract.getCreditorId().equals(userId)) {
            throw ContractChangeErrorCode.ONLY_CREDITOR_CAN_REQUEST_CHANGE.toException();
        }

        if(contract.getStatus() != ContractStatus.COMPLETED) {
            throw ContractChangeErrorCode.CONTRACT_NOT_COMPLETED.toException();
        }

        boolean hasPendingRequest = contractChangeMapper.findByContractId(contractId).stream()
                .anyMatch(changeRequest -> "PENDING".equals(changeRequest.getStatus()));

        if (hasPendingRequest) {
            throw ContractChangeErrorCode.DUPLICATE_PENDING_REQUEST.toException();
        }

        LoanContractChangeDTO changeDTO = LoanContractChangeDTO.builder()
                .changeReason(request.getChangeReason())
                .newMaturityDate(request.getNewMaturityDate())
                .newInterestRate(request.getNewInterestRate())
                .newRepaymentType(request.getNewRepaymentType())
                .newRepaymentDate(request.getNewRepaymentDate())
                .userId(userId)
                .contractId(contractId)
                .status("PENDING")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        contractChangeMapper.insert(changeDTO);

        log.info("계약 변경 요청 생성: contractId={}, userId={}, changeReason={}",
                contractId, userId, request.getChangeReason());
        return changeDTO;
    }
}

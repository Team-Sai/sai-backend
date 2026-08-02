package org.teamsai.saibackend.domain.contractchange.service;


import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.teamsai.saibackend.domain.contractchange.dto.request.ContractChangeRequest;
import org.teamsai.saibackend.domain.contractchange.dto.LoanContractChangeDTO;
import org.teamsai.saibackend.domain.contractchange.dto.LoanContractReadDTO;
import org.teamsai.saibackend.domain.contractchange.exception.ContractChangeErrorCode;
import org.teamsai.saibackend.domain.contractchange.mapper.ContractChangeMapper;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ContractChangeService {

    private final ContractChangeMapper contractChangeMapper;

    public LoanContractReadDTO getContract (Long contractId, Long userId) {
        LoanContractReadDTO contract = contractChangeMapper.findById(contractId)
                .orElseThrow(ContractChangeErrorCode.CONTRACT_NOT_FOUND::toException);

        if (!contract.getCreditorId().equals(userId) && !contract.getDebtorId().equals(userId)) {
            throw ContractChangeErrorCode.FORBIDDEN_CONTRACT_ACCESS.toException();
        }
        return contract;
    }

    @Transactional
    public LoanContractChangeDTO requestChange(Long contractId, ContractChangeRequest request, Long userId) {

        getContract(contractId, userId);

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

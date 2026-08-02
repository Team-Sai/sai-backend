package org.teamsai.saibackend.domain.contractchange.service;


import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.teamsai.saibackend.domain.contractchange.dto.ContractChangeRequest;
import org.teamsai.saibackend.domain.contractchange.dto.LoanContractChangeDTO;
import org.teamsai.saibackend.domain.contractchange.dto.LoanContractReadDTO;
import org.teamsai.saibackend.domain.contractchange.exception.ContractChangeErrorCode;
import org.teamsai.saibackend.domain.contractchange.mapper.ContractChangeMapper;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ContractChangeService {

    private final ContractChangeMapper contractChangeMapper;

    public LoanContractReadDTO getContract (Long contractId) {
        return contractChangeMapper.findById(contractId).orElseThrow(
                ContractChangeErrorCode.CONTRACT_NOT_FOUND::toException);

    }

    @Transactional
    public LoanContractChangeDTO requestChange(Long contractId, ContractChangeRequest request) {

        getContract(contractId);

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
                .userId(request.getUserId())
                .contractId(contractId)
                .status("PENDING")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        contractChangeMapper.insert(changeDTO);
        return changeDTO;
    }
}

package org.teamsai.saibackend.domain.contractchange.service;


import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.teamsai.saibackend.domain.contract.dto.request.ContractStatus;
import org.teamsai.saibackend.domain.contract.dto.response.LoanContractResponse;
import org.teamsai.saibackend.domain.contract.mapper.LoanContractMapper;
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
    private final LoanContractMapper loanContractMapper;
    private final LoanContractService loanContractService;

    public LoanContractResponse getContract(Long contractId, Long userID) {
        LoanContractResponse contract = loanContractService.findContract(contractId, userID);

        if(contract.getStatus() != ContractStatus.COMPLETED) {
            throw ContractChangeErrorCode.CONTRACT_NOT_COMPLETED.toException();
        }
        return contract;
    }

    //차용증 변경 요청 후 계약서 테이블에 저장
    @Transactional
    public LoanContractChangeDTO requestChange(Long contractId, ContractChangeRequest request, Long userId) {

        LoanContractResponse contract = loanContractService.findContract(contractId, userId);

        boolean isCreditor = contract.getCreditorId().equals(userId);
        boolean isDebtor = contract.getDebtorId().equals(userId);

        if (!isCreditor && !isDebtor) {
            throw ContractChangeErrorCode.NOT_CONTRACT_PARTY.toException();
        }

        if (contract.getStatus() != ContractStatus.COMPLETED) {
            throw ContractChangeErrorCode.CONTRACT_NOT_COMPLETED.toException();
        }

        boolean hasPendingRequest = contractChangeMapper.findByContractId(contractId).stream()
                .anyMatch(changeRequest -> ContractStatus.PENDING.equals(changeRequest.getStatus()));

        if (hasPendingRequest) {
            throw ContractChangeErrorCode.DUPLICATE_PENDING_REQUEST.toException();
        }

        LocalDateTime now = LocalDateTime.now();

        LoanContractChangeDTO changeDTO = LoanContractChangeDTO.builder()
                .changeReason(request.getChangeReason())
                .newMaturityDate(request.getNewMaturityDate())
                .newInterestRate(request.getNewInterestRate())
                .newRepaymentType(request.getNewRepaymentType())
                .newRepaymentDate(request.getNewRepaymentDate())
                .userId(userId)
                .contractId(contractId)
                .status(ContractStatus.PENDING)
                .createdAt(now)
                .updatedAt(now)
                .build();

        contractChangeMapper.insert(changeDTO);

        loanContractMapper.insertChangedContract(
                contractId,
                contract.getCreditorId(),
                contract.getDebtorId(),
                contract.getPrincipalAmount(),
                request.getNewInterestRate(),
                request.getNewRepaymentType(),
                contract.getStartDate(),
                request.getNewMaturityDate(),
                contract.getRepaymentDay(),
                contract.getCreditorAddress(),
                contract.getDebtorAddress(),
                contract.getContractAlias(),
                contract.getTerms(),
                ContractStatus.PENDING,
                now,
                now
        );

        log.info("계약 변경 요청 생성 및 차용증 재저장 완료: contractId={}, userId={}",
                contractId, userId);

        return changeDTO;
    }
}

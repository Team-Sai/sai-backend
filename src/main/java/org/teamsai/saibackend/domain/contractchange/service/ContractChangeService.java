package org.teamsai.saibackend.domain.contractchange.service;


import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.teamsai.saibackend.domain.contract.dto.request.ContractStatus;
import org.teamsai.saibackend.domain.contract.dto.request.RepaymentMethod;
import org.teamsai.saibackend.domain.contract.dto.response.ChangeLoanContractResponse;
import org.teamsai.saibackend.domain.contract.dto.response.LoanContractResponse;
import org.teamsai.saibackend.domain.contract.service.LoanContractService;
import org.teamsai.saibackend.domain.contractchange.dto.ChangeRequestStatus;
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


    public void checkAccess(Long contractId, Long userId) {
        getContract(contractId, userId);
    }

    public LoanContractResponse getContract(Long contractId, Long userID) {
        LoanContractResponse contract = loanContractService.findContract(contractId, userID);

        if(contract.getStatus() != ContractStatus.COMPLETED) {
            throw ContractChangeErrorCode.CONTRACT_NOT_COMPLETED.toException();
        }
        return contract;
    }


    public LoanContractChangeDTO getChangeRequest(Long changeRequestId) {
        return contractChangeMapper.findByChangeRequestId(changeRequestId)
                .orElseThrow(ContractChangeErrorCode.CHANGE_REQUEST_NOT_FOUND::toException);

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

        if (!isCreditor) {
            throw ContractChangeErrorCode.NOT_CREDITOR.toException();
        }

        if (contract.getStatus() != ContractStatus.COMPLETED) {
            throw ContractChangeErrorCode.CONTRACT_NOT_COMPLETED.toException();
        }

        boolean hasPendingRequest = contractChangeMapper.findByContractId(contractId).stream()
                .anyMatch(changeRequest -> ChangeRequestStatus.PENDING.equals(changeRequest.getStatus()));

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
                .status(ChangeRequestStatus.PENDING)
                .createdAt(now)
                .updatedAt(now)
                .build();

        contractChangeMapper.insert(changeDTO);

        ChangeLoanContractResponse newContractDTO = ChangeLoanContractResponse.builder()
                .previousContractId(contractId)
                .creditorId(contract.getCreditorId())
                .debtorId(contract.getDebtorId())
                .principalAmount(contract.getPrincipalAmount())
                .interestRate(request.getNewInterestRate())
                .repaymentType(RepaymentMethod.valueOf(request.getNewRepaymentType()))
                .startDate(contract.getStartDate())
                .maturityDate(request.getNewMaturityDate())
                .repaymentDay(request.getNewRepaymentDate())
                .creditorAddress(contract.getCreditorAddress())
                .debtorAddress(contract.getDebtorAddress())
                .contractAlias(contract.getContractAlias())
                .terms(contract.getTerms())
                .status(ContractStatus.PENDING)
                .createdAt(now)
                .updatedAt(now)
                .build();

        loanContractService.insertChangedContract(newContractDTO);

        log.info("계약 변경 요청 생성 및 차용증 재저장 완료: contractId={}, userId={}",
                contractId, userId);

        return changeDTO;
    }
}

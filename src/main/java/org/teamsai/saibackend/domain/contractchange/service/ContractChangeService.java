package org.teamsai.saibackend.domain.contractchange.service;


import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.teamsai.saibackend.domain.contract.dto.request.ContractStatus;
import org.teamsai.saibackend.domain.contract.dto.request.RepaymentMethod;
import org.teamsai.saibackend.domain.contract.dto.response.ChangeLoanContractResponse;
import org.teamsai.saibackend.domain.contract.dto.response.LoanContractResponse;
import org.teamsai.saibackend.domain.contract.event.ContractChangeApprovedEvent;
import org.teamsai.saibackend.domain.contract.service.LoanContractService;
import org.teamsai.saibackend.domain.contractchange.type.ChangeRequestStatus;
import org.teamsai.saibackend.domain.contractchange.dto.LoanContractChangeDTO;
import org.teamsai.saibackend.domain.contractchange.dto.request.ContractChangeRequest;
import org.teamsai.saibackend.domain.contractchange.exception.ContractChangeErrorCode;
import org.teamsai.saibackend.domain.contractchange.mapper.ContractChangeMapper;
import org.teamsai.saibackend.domain.contractrepaymentschedule.service.RepaymentScheduleService;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ContractChangeService {

    private final ContractChangeMapper contractChangeMapper;
    private final LoanContractService loanContractService;
    private final RepaymentScheduleService repaymentScheduleService;


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

        List<LoanContractChangeDTO> existingRequests = contractChangeMapper.findByContractId(contractId);

        boolean hasPendingRequest = existingRequests.stream()
                .anyMatch(changeRequest -> ChangeRequestStatus.PENDING.equals(changeRequest.getStatus()));

        if (hasPendingRequest) {
            throw ContractChangeErrorCode.DUPLICATE_PENDING_REQUEST.toException();
        }

        boolean alreadySuperseded = existingRequests.stream()
                .anyMatch(changeRequest -> ChangeRequestStatus.APPROVED.equals(changeRequest.getStatus()));

        if (alreadySuperseded) {
            throw ContractChangeErrorCode.CONTRACT_ALREADY_SUPERSEDED.toException();
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
                .interestRate(request.getNewInterestRate() != null ? request.getNewInterestRate() : contract.getInterestRate())
                .repaymentType(request.getNewRepaymentType() != null ? RepaymentMethod.valueOf(request.getNewRepaymentType()) : contract.getRepaymentType())
                .startDate(contract.getStartDate())
                .maturityDate(request.getNewMaturityDate() != null ? request.getNewMaturityDate() : contract.getMaturityDate())
                .repaymentDay(request.getNewRepaymentDate() != null ? request.getNewRepaymentDate() : contract.getRepaymentDay())
                .creditorAddress(contract.getCreditorAddress())
                .debtorAddress(contract.getDebtorAddress())
                .contractAlias(contract.getContractAlias())
                .terms(request.getNewTerms() != null ? request.getNewTerms() : contract.getTerms())
                .status(ContractStatus.PENDING)
                .createdAt(now)
                .updatedAt(now)
                .build();

        loanContractService.insertChangedContract(newContractDTO);

        log.info("계약 변경 요청 생성 및 차용증 재저장 완료: contractId={}, userId={}",
                contractId, userId);

        return changeDTO;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onContractChangeApproved(ContractChangeApprovedEvent event) {
        Long v2ContractId = event.newContractId();

        LoanContractResponse v2 = loanContractService.getContractForInternalUse(v2ContractId);
        Long v1ContractId = v2.getPreviousContractId();

        LoanContractChangeDTO pendingRequest = contractChangeMapper.findByContractId(v1ContractId).stream()
                .filter(r -> r.getStatus() == ChangeRequestStatus.PENDING)
                .findFirst()
                .orElseThrow(ContractChangeErrorCode.CHANGE_REQUEST_NOT_FOUND::toException);

        contractChangeMapper.updateStatus(pendingRequest.getChangeRequestId(), ChangeRequestStatus.APPROVED);

        repaymentScheduleService.generateChangedSchedule(v1ContractId, v2ContractId);

        log.info("계약 변경 승인 처리 완료: v1ContractId={}, v2ContractId={}, changeRequestId={}",
                v1ContractId, v2ContractId, pendingRequest.getChangeRequestId());
    }
}

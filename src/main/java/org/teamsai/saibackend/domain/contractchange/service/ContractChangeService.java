package org.teamsai.saibackend.domain.contractchange.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.teamsai.saibackend.domain.contract.dto.request.ContractStatus;
import org.teamsai.saibackend.domain.contract.dto.request.RepaymentMethod;
import org.teamsai.saibackend.domain.contract.dto.response.ChangeLoanContractResponse;
import org.teamsai.saibackend.domain.contract.dto.response.LoanContractResponse;
import org.teamsai.saibackend.domain.contract.event.ContractCompletedEvent;
import org.teamsai.saibackend.domain.contract.exception.LoanContractErrorCode;
import org.teamsai.saibackend.domain.contract.service.LoanContractFileService;
import org.teamsai.saibackend.domain.contract.service.LoanContractService;
import org.teamsai.saibackend.domain.contractchange.dto.LoanContractChangeDTO;
import org.teamsai.saibackend.domain.contractchange.dto.request.ContractChangeRequest;
import org.teamsai.saibackend.domain.contractchange.exception.ContractChangeErrorCode;
import org.teamsai.saibackend.domain.contractchange.mapper.ContractChangeMapper;
import org.teamsai.saibackend.domain.contractchange.type.ChangeRequestStatus;
import org.teamsai.saibackend.domain.contractrepaymentschedule.service.RepaymentScheduleService;
import org.teamsai.saibackend.domain.identity.service.IdentityService;
import org.teamsai.saibackend.domain.identity.type.IdentityPurpose;
import org.teamsai.saibackend.domain.notification.service.NotificationService;
import org.teamsai.saibackend.domain.notification.type.NotificationType;
import org.teamsai.saibackend.domain.user.dto.response.UserResponse;
import org.teamsai.saibackend.domain.user.service.UserService;

import java.time.LocalDateTime;
import java.time.Period;
import java.util.List;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ContractChangeService {

    private final ContractChangeMapper contractChangeMapper;
    private final LoanContractService loanContractService;
    private final RepaymentScheduleService repaymentScheduleService;
    private final NotificationService notificationService;
    private final UserService userService;
    private final LoanContractFileService fileService;
    private final IdentityService identityService;
    private final ApplicationEventPublisher eventPublisher;


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

    private LoanContractResponse getPendingChangedContract(Long contractId) {
        return loanContractService.findPendingContractByPreviousId(contractId)
                .orElseThrow(ContractChangeErrorCode.CHANGE_REQUEST_NOT_FOUND::toException);
    }

    public Long getPendingChangedContractId(Long contractId) {
        return getPendingChangedContract(contractId).getContractId();
    }


    @Transactional
    public LoanContractChangeDTO requestChange(
            Long contractId,
            ContractChangeRequest request,
            Long userId
            ) {

        LoanContractResponse contract = loanContractService.findContract(contractId, userId);

        boolean isCreditor = Objects.equals(contract.getCreditorId(), userId);
        boolean isDebtor = Objects.equals(contract.getDebtorId(), userId);

        if (!isCreditor && !isDebtor) {
            throw ContractChangeErrorCode.NOT_CONTRACT_PARTY.toException();
        }

        if (contract.getStatus() != ContractStatus.COMPLETED) {
            throw ContractChangeErrorCode.CONTRACT_NOT_COMPLETED.toException();
        }

        if (contract.getStartDate() != null
                && request.getNewMaturityDate() != null
                && Period.between(contract.getStartDate(), request.getNewMaturityDate()).toTotalMonths() <= 0) {
            throw ContractChangeErrorCode.INVALID_MATURITY_DATE.toException();
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
                .newTerms(request.getNewTerms())
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
                .relationType(contract.getRelationType())
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

    @Transactional
    public LoanContractChangeDTO rejectChange(Long contractId, Long changeRequestId, String returnReason, Long userId) {

        LoanContractResponse contract = loanContractService.findContract(contractId, userId);
        LoanContractChangeDTO changeRequest = getChangeRequest(changeRequestId);

        boolean requesterIsCreditor = Objects.equals(contract.getCreditorId(), changeRequest.getUserId());
        Long approverId = requesterIsCreditor ? contract.getDebtorId() : contract.getCreditorId();

        if(!Objects.equals(approverId, userId)) {
            throw ContractChangeErrorCode.NOT_CONTRACT_PARTY.toException();
        }

        if(!changeRequest.getContractId().equals(contractId)) {
            throw ContractChangeErrorCode.CHANGE_REQUEST_NOT_FOUND.toException();
        }

        if(changeRequest.getStatus() != ChangeRequestStatus.PENDING)  {
            throw ContractChangeErrorCode.ALREADY_BEING_REQUEST.toException();
        }

        int updatedRows = contractChangeMapper.updateStatusWithReturnReason(changeRequestId, ChangeRequestStatus.REJECTED, returnReason);
        if(updatedRows == 0) {
            throw ContractChangeErrorCode.ALREADY_BEING_REQUEST.toException();
        }
        try {
            UserResponse rejectorInfo = userService.getMyInfo(userId);

            notificationService.create(
                    changeRequest.getUserId(),
                    NotificationType.CONTRACT_CHANGE,
                    "계약 변경 요청 반려",
                    rejectorInfo.getName() + "님이 변경 요청을 반려했습니다.",
                    contractId,
                    changeRequestId
            );
        } catch (Exception e) {
            log.error("계약 변경 반려 알림 발송 실패: contractId={}, changeRequestId={}, rror={}",
                    contractId, changeRequestId, e.getMessage(), e);
        }

        LoanContractResponse v2 = getPendingChangedContract(contractId);

        loanContractService.rejectChangedContract(v2.getContractId());

        log.info("계약 변경 요청 반려 처리 완료: contractId={}, changeRequestId={}", contractId, changeRequestId);

        return getChangeRequest(changeRequestId);

    }

    @Transactional
    public ContractStatus approveChange(Long contractId, Long userId, MultipartFile signature, String identityVerificationId) {

        LoanContractResponse contract = loanContractService.getContractForInternalUse(contractId);

        boolean isCreditor = Objects.equals(contract.getCreditorId(), userId);
        boolean isDebtor = Objects.equals(contract.getDebtorId(), userId);

        if (!isCreditor && !isDebtor) {
            throw LoanContractErrorCode.CONTRACT_ACCESS_DENIED.toException();
        }

        if (contract.getStatus() == ContractStatus.COMPLETED) {
            throw LoanContractErrorCode.CONTRACT_ALREADY_COMPLETED.toException();
        }

        if (contract.getPreviousContractId() == null) {
            throw LoanContractErrorCode.NOT_A_CHANGE_CONTRACT.toException();
        }

        Long v1ContractId = contract.getPreviousContractId();

        LoanContractChangeDTO changeRequest = contractChangeMapper.findByContractId(v1ContractId).stream()
                .filter(r -> r.getStatus() == ChangeRequestStatus.PENDING)
                .findFirst()
                .orElseThrow(ContractChangeErrorCode.CHANGE_REQUEST_NOT_FOUND::toException);

        boolean requesterIsCreditor = Objects.equals(contract.getCreditorId(), changeRequest.getUserId());
        Long approverId = requesterIsCreditor ? contract.getDebtorId() : contract.getCreditorId();

        if (!Objects.equals(approverId, userId)) {
            throw ContractChangeErrorCode.NOT_CONTRACT_PARTY.toException();
        }

        identityService.consume(userId, identityVerificationId, IdentityPurpose.LOAN_CONTRACT);

        String savedPath = fileService.saveSignatureFile(contractId, signature);

        if (isCreditor) {
            loanContractService.updateCreditorSignatureOnly(contractId, savedPath);
        } else {
            loanContractService.updateDebtorSignatureOnly(contractId, savedPath);
        }

        int updatedRows = contractChangeMapper.updateStatus(changeRequest.getChangeRequestId(), ChangeRequestStatus.APPROVED);
        if (updatedRows == 0) {
            throw ContractChangeErrorCode.ALREADY_BEING_REQUEST.toException();
        }

        loanContractService.supersedeContract(v1ContractId);

        repaymentScheduleService.generateChangedSchedule(v1ContractId, contractId);

        log.info("계약 변경 승인 처리 완료: v1ContractId={}, v2ContractId={}, changeRequestId={}",
                v1ContractId, contractId, changeRequest.getChangeRequestId());

        LoanContractResponse completedContract = loanContractService.buildCompletedSnapshot(contract, isCreditor, savedPath);
        eventPublisher.publishEvent(new ContractCompletedEvent(completedContract));

        try {
            UserResponse approverInfo = userService.getMyInfo(userId);

            notificationService.create(
                    changeRequest.getUserId(),
                    NotificationType.CONTRACT_CHANGE,
                    "계약 변경 승인 완료",
                    approverInfo.getName() + "님이 신청하신 계약 변경 요청을 승인했습니다.",
                    contractId
            );
        } catch (Exception e) {
            log.error("계약 변경 승인 알림 발송 실패: v2ContractId={}, userId={}, error={}",
                    contractId, changeRequest.getUserId(), e.getMessage(), e);
        }

        return ContractStatus.COMPLETED;
    }

    public boolean hasPendingChangeRequest(Long contractId) {
        List<LoanContractChangeDTO> existingRequests = contractChangeMapper.findByContractId(contractId);
        return existingRequests.stream()
                .anyMatch(changeRequest -> ChangeRequestStatus.PENDING.equals(changeRequest.getStatus()));
    }

    @Transactional
    public void cancelChangeRequest(Long contractId, Long changeRequestId, Long userId) {
        LoanContractChangeDTO changeDTO = getChangeRequest(changeRequestId);

        if (!changeDTO.getContractId().equals(contractId)) {
            throw ContractChangeErrorCode.CHANGE_REQUEST_NOT_FOUND.toException();
        }

        if (!changeDTO.getUserId().equals(userId)) {
            throw ContractChangeErrorCode.NOT_CONTRACT_PARTY.toException();
        }

        if (changeDTO.getStatus() != ChangeRequestStatus.PENDING) {
            throw ContractChangeErrorCode.ALREADY_BEING_REQUEST.toException();
        }

        int updatedRows = contractChangeMapper.cancelPendingUnsignedRequest(changeRequestId);
        if (updatedRows == 0) {
            throw ContractChangeErrorCode.ALREADY_SIGNED.toException();
        }

        LoanContractResponse v2 = getPendingChangedContract(contractId);
        loanContractService.rejectChangedContract(v2.getContractId());

        log.info("계약 변경 요청 취소 처리 완료: contractId={}, changeRequestId={}, userId={}",
                contractId, changeRequestId, userId);
    }

    @Transactional
    public LoanContractChangeDTO submitRequesterSignature(
            Long contractId,
            Long changeRequestId,
            Long userId,
            MultipartFile signature,
            String identityVerificationId

    ) {


        identityService.consume(userId, identityVerificationId, IdentityPurpose.LOAN_CONTRACT);

        LoanContractChangeDTO changeDTO = getChangeRequest(changeRequestId);

        if (!changeDTO.getContractId().equals(contractId)) {
            throw ContractChangeErrorCode.CHANGE_REQUEST_NOT_FOUND.toException();
        }

        if (!changeDTO.getUserId().equals(userId)) {
            throw ContractChangeErrorCode.NOT_CONTRACT_PARTY.toException();
        }

        if (changeDTO.getStatus() != ChangeRequestStatus.PENDING) {
            throw ContractChangeErrorCode.ALREADY_BEING_REQUEST.toException();
        }

        String savedPath = fileService.saveSignatureFile(changeRequestId, signature);

        int updatedRows = contractChangeMapper.updateRequesterSignature(changeRequestId, savedPath);
        if (updatedRows == 0) {
            throw ContractChangeErrorCode.ALREADY_SIGNED.toException();
        }

        LoanContractResponse contract = loanContractService.findContract(contractId, userId);
        UserResponse requesterInfo = userService.getMyInfo(userId);

        boolean isCreditor = Objects.equals(contract.getCreditorId(), userId);
        Long recipientId = isCreditor ? contract.getDebtorId() : contract.getCreditorId();

        if(recipientId != null) {
            notificationService.create(
                    recipientId,
                    NotificationType.CONTRACT_CHANGE,
                    "계약 변경 요청",
                    requesterInfo.getName() + "님으로부터 계약 내용 변경 요청이 도착했습니다.",
                    contractId,
                    changeRequestId
            );
        }else{
            log.warn("계약 변경 요청 알림 발송 실패 - recipientId가 null입니다. contractId={}, changeRequestId={}",
            contractId, changeRequestId);
        }

        return getChangeRequest(changeRequestId);
    }
}

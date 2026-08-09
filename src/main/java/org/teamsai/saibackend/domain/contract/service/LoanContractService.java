package org.teamsai.saibackend.domain.contract.service;


import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.teamsai.saibackend.domain.contract.dto.request.ContractStatus;
import org.teamsai.saibackend.domain.contract.dto.request.LoanContractRequest;
import org.teamsai.saibackend.domain.contract.dto.response.ChangeLoanContractResponse;
import org.teamsai.saibackend.domain.contract.dto.response.LoanContractResponse;
import org.teamsai.saibackend.domain.contract.event.ContractChangeApprovedEvent;
import org.teamsai.saibackend.domain.contract.event.ContractCreatedEvent;
import org.teamsai.saibackend.domain.contract.exception.LoanContractErrorCode;
import org.teamsai.saibackend.domain.contract.mapper.LoanContractMapper;
import org.teamsai.saibackend.domain.identity.service.IdentityService;
import org.teamsai.saibackend.domain.identity.type.IdentityPurpose;
import org.teamsai.saibackend.domain.user.service.UserService;

import java.util.Objects;

@Slf4j
@Service
@AllArgsConstructor
public class LoanContractService {
    private final LoanContractMapper contractMapper;
    private final LoanContractFileService fileService;
    private final ContractAccountService contractAccountService;
    private final UserService userService;
    private final IdentityService identityService;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public Long createContract(LoanContractRequest request, Long userId) {

        identityService.consume(
                userId,
                request.getIdentityVerificationId(),
                IdentityPurpose.LOAN_CONTRACT
        );

        userService.getMyInfo(userId);

        contractMapper.insertByContract(request, userId);

        contractAccountService.createContractAccount(request.getContractId(), userId, request.getSelectedLinkedAccountId());

        eventPublisher.publishEvent(new ContractCreatedEvent(request.getContractId()));   // 방송만 함

        return request.getContractId();
    }

    @Transactional
    public ContractStatus submitCreditorSignature(Long contractId, Long userId, String debtorUserToken, MultipartFile signature) {
        LoanContractResponse contract = contractMapper.findContractById(contractId)
                .orElseThrow(LoanContractErrorCode.CONTRACT_NOT_FOUND::toException);

        if (!contract.getCreditorId().equals(userId)) {
            throw LoanContractErrorCode.CONTRACT_ACCESS_DENIED.toException();
        }

        String savedPath = fileService.saveSignatureFile(contractId, signature);

        Long debtorId = userService.findRequestTarget(userId, debtorUserToken).getUserId();

        contractMapper.updateCreditorSignature(contractId, savedPath, debtorId, ContractStatus.PENDING);

        return ContractStatus.PENDING;
    }

    @Transactional
    public void linkDebtor(Long contractId, Long userId) {
        LoanContractResponse contract = contractMapper.findContractById(contractId)
                .orElseThrow(LoanContractErrorCode.CONTRACT_NOT_FOUND::toException);

        if (contract.getDebtorId() != null) {
            throw LoanContractErrorCode.DEBTOR_ALREADY_LINKED.toException();
        }

        if (userId.equals(contract.getCreditorId())) {
            throw LoanContractErrorCode.CANNOT_CREATE_CONTRACT_TO_SELF.toException();
        }

        userService.getMyInfo(userId);

        contractMapper.updateDebtorId(contractId, userId);
    }

    @Transactional
    public ContractStatus submitDebtorSignature(Long contractId, Long userId, String debtorAddress, MultipartFile signature) {

        if (!StringUtils.hasText(debtorAddress)) {
            throw LoanContractErrorCode.DEBTOR_ADDRESS_REQUIRED.toException();
        }

        LoanContractResponse contract = contractMapper.findContractById(contractId)
                .orElseThrow(LoanContractErrorCode.CONTRACT_NOT_FOUND::toException);

        if (!Objects.equals(contract.getDebtorId(), userId)) {
            throw LoanContractErrorCode.CONTRACT_ACCESS_DENIED.toException();
        }

        if (contract.getStatus() == ContractStatus.COMPLETED) {
            throw LoanContractErrorCode.CONTRACT_ALREADY_COMPLETED.toException();
        }


        String savedPath = fileService.saveSignatureFile(contractId, signature);
        contractMapper.updateDebtorSignature(contractId, debtorAddress, savedPath, ContractStatus.COMPLETED);

        if (contract.getPreviousContractId() != null) {
            eventPublisher.publishEvent(new ContractChangeApprovedEvent(contractId));
        }

        return ContractStatus.COMPLETED;
    }

    public LoanContractResponse findContract(Long contractId, Long userId) {
        LoanContractResponse contract = contractMapper.findContractById(contractId)
                .orElseThrow(LoanContractErrorCode.CONTRACT_NOT_FOUND::toException);

        boolean isParty = contract.getCreditorId().equals(userId) || Objects.equals(contract.getDebtorId(), userId);
        if (!isParty) {
            throw LoanContractErrorCode.CONTRACT_ACCESS_DENIED.toException();
        }

        return withPartyInfo(contract);
    }

    private LoanContractResponse withPartyInfo(LoanContractResponse contract) {
        var creditor = userService.getMyInfo(contract.getCreditorId());

        LoanContractResponse.LoanContractResponseBuilder enriched = contract.toBuilder()
                .creditorName(creditor.getName())
                .creditorBirthDate(creditor.getBirthDate().toString());

        if (contract.getDebtorId() != null) {
            var debtor = userService.getMyInfo(contract.getDebtorId());

            enriched.debtorName(debtor.getName())
                    .debtorBirthDate(debtor.getBirthDate().toString());
        }

        return enriched.build();
    }

    @Transactional
    public void insertChangedContract(ChangeLoanContractResponse changedContract) {
        contractMapper.insertChangedContract(changedContract);
    }

    public LoanContractResponse getContractForInternalUse(Long contractId) {
        return contractMapper.findContractById(contractId)
                .orElseThrow(LoanContractErrorCode.CONTRACT_NOT_FOUND::toException);
    }

}

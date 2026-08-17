package org.teamsai.saibackend.domain.contract.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.teamsai.saibackend.domain.account.dto.response.LinkedBankAccountResponse;
import org.teamsai.saibackend.domain.account.dto.type.ConnectionStatus;
import org.teamsai.saibackend.domain.account.service.LinkedBankAccountService;
import org.teamsai.saibackend.domain.contract.dto.ContractAccountDTO;
import org.teamsai.saibackend.domain.contract.dto.ContractAccountStatus;
import org.teamsai.saibackend.domain.contract.dto.request.ContractStatus;
import org.teamsai.saibackend.domain.contract.dto.response.LoanContractResponse;
import org.teamsai.saibackend.domain.contract.exception.LoanContractErrorCode;
import org.teamsai.saibackend.domain.contract.mapper.ContractAccountMapper;
import org.teamsai.saibackend.domain.contract.mapper.LoanContractMapper;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ContractAccountService {

    private final ContractAccountMapper contractAccountMapper;
    private final LoanContractMapper loanContractMapper;
    private final LinkedBankAccountService linkedBankAccountService;

    @Transactional(readOnly = true)
    public List<LinkedBankAccountResponse> getSelectableAccounts(Long userId) {
        return linkedBankAccountService.getLinkedAccounts(userId).stream()
                .filter(account -> ConnectionStatus.AVAILABLE.name().equals(account.connectionStatus()))
                .toList();
    }

    @Transactional
    public void createContractAccount(Long contractId, Long userId, Long linkedAccountId) {
        if (linkedAccountId == null) return;

        contractAccountMapper.selectContractForUpdate(contractId);

        validateSelectable(userId, linkedAccountId);

        try {
            retireActiveAccount(contractId, ContractAccountStatus.REPLACED);
        } catch (RuntimeException e) {
        }

        insertActiveAccount(contractId, linkedAccountId);
    }

    @Transactional
    public void changeContractAccount(Long contractId, Long userId, Long newLinkedAccountId) {
        contractAccountMapper.selectContractForUpdate(contractId);

        validateContractOwner(contractId, userId);
        validateSelectable(userId, newLinkedAccountId);

        retireActiveAccount(contractId, ContractAccountStatus.REPLACED);
        insertActiveAccount(contractId, newLinkedAccountId);
    }

    @Transactional
    public void deactivateContractAccount(Long contractId, Long userId) {
        contractAccountMapper.selectContractForUpdate(contractId);

        validateContractOwner(contractId, userId);
        retireActiveAccount(contractId, ContractAccountStatus.DISABLED);
    }

    private void retireActiveAccount(Long contractId, ContractAccountStatus status) {

        int updatedRows = contractAccountMapper.updateContractAccountStatus(contractId, status);
        if (updatedRows == 0) {
            throw LoanContractErrorCode.CONTRACT_ACCOUNT_NOT_FOUND.toException();
        }
    }

    private void validateContractOwner(Long contractId, Long userId) {
        LoanContractResponse contract = loanContractMapper.findContractById(contractId)
                .orElseThrow(LoanContractErrorCode.CONTRACT_NOT_FOUND::toException);

        if (!contract.getCreditorId().equals(userId)) {
            throw LoanContractErrorCode.CONTRACT_ACCESS_DENIED.toException();
        }


        if (contract.getStatus() != ContractStatus.DRAFT && contract.getStatus() != ContractStatus.PENDING) {
            throw LoanContractErrorCode.CONTRACT_ACCESS_DENIED.toException();
        }
    }

    private void validateSelectable(Long userId, Long linkedAccountId) {
        boolean isSelectable = getSelectableAccounts(userId).stream()
                .anyMatch(account -> account.linkedAccountId().equals(linkedAccountId));

        if (!isSelectable) {
            throw LoanContractErrorCode.INVALID_LINKED_ACCOUNT.toException();
        }
    }

    private void insertActiveAccount(Long contractId, Long linkedAccountId) {
        ContractAccountDTO contractAccount = ContractAccountDTO.builder()
                .contractId(contractId)
                .linkedAccountId(linkedAccountId)
                .accountStatus(ContractAccountStatus.ACTIVE)
                .selectedAt(LocalDateTime.now())
                .build();

        contractAccountMapper.insertContractAccount(contractAccount);
    }
}
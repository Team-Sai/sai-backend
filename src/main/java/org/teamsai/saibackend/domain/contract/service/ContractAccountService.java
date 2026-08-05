package org.teamsai.saibackend.domain.contract.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.teamsai.saibackend.domain.account.dto.response.LinkedBankAccountResponse;
import org.teamsai.saibackend.domain.account.dto.type.ConnectionStatus;
import org.teamsai.saibackend.domain.account.service.LinkedBankAccountService;
import org.teamsai.saibackend.domain.contract.dto.ContractAccountDTO;
import org.teamsai.saibackend.domain.contract.dto.ContractAccountStatus;
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
                .filter(account -> ConnectionStatus.AVAILABLE.name()
                .equals(account.connectionStatus()))
                .toList();
    }

    @Transactional
    public void setupContractAccount(Long contractId, Long userId, Long linkedAccountId) {
        if (linkedAccountId == null) {
            return;
        }

        validateSelectable(userId, linkedAccountId);
        insertActiveAccount(contractId, linkedAccountId);
    }

    @Transactional
    public void changeContractAccount(Long contractId, Long userId, Long newLinkedAccountId) {
        validateContractOwner(contractId, userId);
        contractAccountMapper.findActiveAccountByContractId(contractId)
                .orElseThrow(LoanContractErrorCode.CONTRACT_ACCOUNT_NOT_FOUND::toException);
        validateSelectable(userId, newLinkedAccountId);

        contractAccountMapper.updateContractAccountStatus(contractId, ContractAccountStatus.REPLACED);
        insertActiveAccount(contractId, newLinkedAccountId);
    }

    @Transactional
    public void deactivateContractAccount(Long contractId, Long userId) {
        validateContractOwner(contractId, userId);
        contractAccountMapper.findActiveAccountByContractId(contractId)
                .orElseThrow(LoanContractErrorCode.CONTRACT_ACCOUNT_NOT_FOUND::toException);

        contractAccountMapper.updateContractAccountStatus(contractId, ContractAccountStatus.DISABLED);
    }

    private void validateContractOwner(Long contractId, Long userId) {
        LoanContractResponse contract = loanContractMapper.findContractById(contractId)
                .orElseThrow(LoanContractErrorCode.CONTRACT_NOT_FOUND::toException);

        if (!contract.getCreditorId().equals(userId)) {
            throw LoanContractErrorCode.CONTRACT_ACCESS_DENIED.toException();
        }
    }

    private void validateSelectable(Long userId, Long linkedAccountId) {
        if (!isSelectableAccount(userId, linkedAccountId)) {
            throw LoanContractErrorCode.INVALID_LINKED_ACCOUNT.toException();
        }
    }

    private boolean isSelectableAccount(Long userId, Long linkedAccountId) {
        return getSelectableAccounts(userId).stream()
                .anyMatch(account -> account.linkedAccountId().equals(linkedAccountId));
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

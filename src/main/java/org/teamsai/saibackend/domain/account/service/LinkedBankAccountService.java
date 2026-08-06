package org.teamsai.saibackend.domain.account.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientException;
import org.teamsai.saibackend.domain.account.dto.*;
import org.teamsai.saibackend.domain.account.dto.request.LinkAccountRequest;
import org.teamsai.saibackend.domain.account.dto.response.AccountDetailResponse;
import org.teamsai.saibackend.domain.account.dto.response.LinkedBankAccountResponse;
import org.teamsai.saibackend.domain.account.dto.type.ConnectionStatus;
import org.teamsai.saibackend.domain.account.exception.AccountErrorCode;
import org.teamsai.saibackend.domain.account.mapper.LinkedBankAccountMapper;
import org.teamsai.saibackend.domain.user.service.UserService;
import org.teamsai.saibackend.global.client.MockBankClient;

import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class LinkedBankAccountService {

    private final LinkedBankAccountMapper linkedBankAccountMapper;
    private final UserService userService;
    private final MockBankClient mockBankClient;

    public List<LinkedBankAccountResponse> linkSelectedAccounts(Long userId, LinkAccountRequest request) {
        String userKey = userService.getUserKeyByUserId(userId);
        LocalDateTime now = LocalDateTime.now();

        List<LinkedBankAccountDTO> candidates = request.selectedAccounts().stream()
                .map(selected -> toLinkedAccountDTO(
                        userId,
                        selected.accountId(),
                        userKey,
                        selected.accountAlias(),
                        now
                ))
                .toList();

        List<LinkedBankAccountDTO> savedDtos = insertAllSkippingDuplicates(candidates);

        return savedDtos.stream().map(LinkedBankAccountResponse::from).toList();
    }

    @Transactional
    public List<LinkedBankAccountResponse> linkAccountsByIds(Long userId, String userKey, List<Long> accountIds) {
        Set<Long> alreadyLinkedIds = new HashSet<>(getLinkedAccountIds(userId));

        List<Long> newAccountIds = accountIds.stream()
                .filter(accountId -> !alreadyLinkedIds.contains(accountId))
                .toList();

        LocalDateTime now = LocalDateTime.now();

        List<LinkedBankAccountDTO> candidates = newAccountIds.stream()
                .map(accountId -> {
                    AccountDetailResponse detail = fetchAccountDetail(accountId, userKey);
                    return toLinkedAccountDTO(
                            userId,
                            accountId,
                            detail,
                            detail.accountName(),
                            now
                    );
                })
                .toList();

        List<LinkedBankAccountDTO> savedDtos = insertAllSkippingDuplicates(candidates);

        return savedDtos.stream().map(LinkedBankAccountResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<LinkedBankAccountResponse> getLinkedAccounts(Long userId) {
        if (userId == null) {
            return Collections.emptyList();
        }

        List<LinkedBankAccountDTO> linkedAccounts = linkedBankAccountMapper.selectLinkedAccountsByUserId(userId);
        if (linkedAccounts == null || linkedAccounts.isEmpty()) {
            return Collections.emptyList();
        }

        return linkedAccounts.stream()
                .map(LinkedBankAccountResponse::from)
                .toList();
    }

    public List<Long> getLinkedAccountIds(Long userId) {
        if (userId == null) {
            return Collections.emptyList();
        }

        List<LinkedBankAccountDTO> linkedAccounts = linkedBankAccountMapper.selectLinkedAccountsByUserId(userId);
        if (linkedAccounts == null || linkedAccounts.isEmpty()) {
            return Collections.emptyList();
        }

        return linkedAccounts.stream()
                .map(LinkedBankAccountDTO::getAccountId)
                .toList();
    }

    private List<LinkedBankAccountDTO> insertAllSkippingDuplicates(List<LinkedBankAccountDTO> candidates) {
        List<LinkedBankAccountDTO> savedDtos = new ArrayList<>();

        for (LinkedBankAccountDTO dto : candidates) {
            try {
                linkedBankAccountMapper.insertOne(dto);
                savedDtos.add(dto);
            } catch (DuplicateKeyException e) {
                log.info(
                        "[LinkedBankAccountService] 이미 연동된 계좌라 저장을 건너뜁니다 - accountId: {}",
                        dto.getAccountId()
                );
            }
        }

        return savedDtos;
    }

    private AccountDetailResponse fetchAccountDetail(Long accountId, String userKey) {
        try {
            return mockBankClient.getAccountDetail(accountId, userKey);
        } catch (RestClientException e) {
            log.warn("[LinkedBankAccountService] 계좌 상세 조회 실패 - accountId: {}", accountId, e);
            throw AccountErrorCode.BANK_SERVER_UNAVAILABLE.toException();
        }
    }

    private LinkedBankAccountDTO toLinkedAccountDTO(
            Long userId,
            Long accountId,
            String userKey,
            String accountAlias,
            LocalDateTime now
    ) {
        AccountDetailResponse detail = fetchAccountDetail(accountId, userKey);
        return toLinkedAccountDTO(userId, accountId, detail, accountAlias, now);
    }

    private LinkedBankAccountDTO toLinkedAccountDTO(
            Long userId,
            Long accountId,
            AccountDetailResponse detail,
            String accountAlias,
            LocalDateTime now
    ) {
        validateAccountDetail(detail, accountId);

        return LinkedBankAccountDTO.builder()
                .userId(userId)
                .accountId(accountId)
                .bankCode(detail.bankCode())
                .accountNumber(detail.maskedAccountNumber())
                .accountAlias(accountAlias)
                .accountHolderName(detail.accountHolderName())
                .balance(detail.balance())
                .connectionStatus(ConnectionStatus.AVAILABLE)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    private void validateAccountDetail(AccountDetailResponse detail, Long accountId) {
        if (detail == null
                || isBlank(detail.bankCode())
                || isBlank(detail.maskedAccountNumber())
                || isBlank(detail.accountHolderName())
                || detail.balance() == null) {
            log.error(
                    "[LinkedBankAccountService] 사이은행 응답에 필수 필드가 누락됨 - accountId: {}, detail: {}",
                    accountId,
                    detail
            );
            throw AccountErrorCode.INVALID_BANK_RESPONSE.toException();
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
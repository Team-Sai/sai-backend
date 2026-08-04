package org.teamsai.saibackend.domain.account.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

        List<LinkedBankAccountDTO> dtosToSave = request.selectedAccounts().stream()
                .map(selected -> {
                    AccountDetailResponse detail;
                    try {
                        detail = mockBankClient.getAccountDetail(selected.accountId(), userKey);
                    } catch (RestClientException e) {
                        log.warn("[LinkedBankAccountService] 계좌 상세 조회 실패 - accountId: {}", selected.accountId(), e);
                        throw AccountErrorCode.BANK_SERVER_UNAVAILABLE.toException();
                    }

                    return LinkedBankAccountDTO.builder()
                            .userId(userId)
                            .accountId(selected.accountId())
                            .bankCode(detail.bankCode())
                            .accountNumber(detail.accountNumber())
                            .accountAlias(selected.accountAlias())
                            .accountHolderName(detail.accountHolderName())
                            .balance(detail.balance())
                            .connectionStatus(ConnectionStatus.AVAILABLE)
                            .createdAt(now)
                            .updatedAt(now)
                            .build();
                })
                .toList();

        dtosToSave.forEach(linkedBankAccountMapper::insertOne);

        return dtosToSave.stream().map(LinkedBankAccountResponse::from).toList();
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
}

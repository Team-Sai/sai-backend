package org.teamsai.saibackend.domain.account.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.teamsai.saibackend.domain.account.dto.AccountDetailResponse;
import org.teamsai.saibackend.domain.account.dto.LinkableAccountResponse;
import org.teamsai.saibackend.domain.account.dto.LinkedBankAccountDTO;
import org.teamsai.saibackend.domain.account.exception.AccountErrorCode;
import org.teamsai.saibackend.domain.account.mapper.LinkedBankAccountMapper;
import org.teamsai.saibackend.domain.user.service.UserService;
import org.teamsai.saibackend.global.client.MockBankClient;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExternalBankService {

    private final MockBankClient mockBankClient;
    private final UserService userService;
    private final LinkedBankAccountMapper linkedBankAccountMapper;

    public List<LinkableAccountResponse> fetchAvailableAccountsFromBank(Long userId) {
        String userKey = userService.getUserKeyByUserId(userId);
        List<LinkableAccountResponse> allAccounts;
        try {
            allAccounts = mockBankClient.getAccountsByUserKey(userKey);
        } catch (RestClientException e) {
            log.warn("[ExternalBankService] 사이은행 계좌 목록 조회 실패 - userId: {}", userId, e);
            throw AccountErrorCode.BANK_SERVER_UNAVAILABLE.toException();
        }

        Set<Long> linkedMockAccountIds = linkedBankAccountMapper
                .selectLinkedAccountsByUserId(userId)
                .stream()
                .map(LinkedBankAccountDTO::getAccountId)
                .collect(Collectors.toSet());

        return allAccounts.stream()
                .filter(account -> !linkedMockAccountIds.contains(account.accountId()))
                .toList();
    }

    public AccountDetailResponse getAccountDetail(Long accountId, Long userId) {
        String userKey = userService.getUserKeyByUserId(userId);

        List<LinkableAccountResponse> accounts;
        try {
            accounts = mockBankClient.getAccountsByUserKey(userKey);
        } catch (RestClientException e) {
            log.warn("[ExternalBankService] 사이은행 계좌 목록 조회 실패 - userId: {}", userId, e);
            throw AccountErrorCode.BANK_SERVER_UNAVAILABLE.toException();
        }

        boolean owns = accounts.stream()
                .anyMatch(acc -> acc.accountId().equals(accountId));

        if (!owns) {
            throw AccountErrorCode.ACCOUNT_ACCESS_DENIED.toException();
        }

        try {
            return mockBankClient.getAccountDetail(accountId, userKey);
        } catch (RestClientException e) {
            log.warn("[ExternalBankService] 사이은행 계좌 상세 조회 실패 - accountId: {}", accountId, e);
            throw AccountErrorCode.BANK_SERVER_UNAVAILABLE.toException();
        }
    }
}

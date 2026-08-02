package org.teamsai.saibackend.domain.account.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.teamsai.saibackend.domain.account.dto.AccountDetailResponse;
import org.teamsai.saibackend.domain.account.dto.LinkableAccountResponse;
import org.teamsai.saibackend.domain.account.dto.LinkedBankAccountDTO;
import org.teamsai.saibackend.domain.account.mapper.LinkedBankAccountMapper;
import org.teamsai.saibackend.domain.user.service.UserService;
import org.teamsai.saibackend.global.client.MockBankClient;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ExternalBankService {

    private final MockBankClient mockBankClient;
    private final UserService userService;
    private final LinkedBankAccountMapper linkedBankAccountMapper;

    public List<LinkableAccountResponse> fetchAvailableAccountsFromBank(Long userId, String userToken) {
        String userKey = userService.getUserKeyByUserToken(userToken);
        List<LinkableAccountResponse> allAccounts =
                mockBankClient.getAccountsByUserKey(userKey);

        Set<Long> linkedMockAccountIds = linkedBankAccountMapper
                .selectLinkedAccountsByUserId(userId)
                .stream()
                .map(LinkedBankAccountDTO::getAccountId)
                .collect(Collectors.toSet());

        return allAccounts.stream()
                .filter(account -> !linkedMockAccountIds.contains(account.accountId()))
                .toList();
    }

    public AccountDetailResponse getAccountDetail(Long accountId, String userKey) {
        return mockBankClient.getAccountDetail(accountId, userKey);
    }
}

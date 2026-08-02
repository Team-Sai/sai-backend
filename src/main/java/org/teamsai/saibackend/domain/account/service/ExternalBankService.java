package org.teamsai.saibackend.domain.account.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.teamsai.saibackend.domain.account.dto.AccountDetailResponse;
import org.teamsai.saibackend.domain.account.dto.LinkableAccountResponse;
import org.teamsai.saibackend.domain.user.service.UserService;
import org.teamsai.saibackend.global.client.MockBankClient;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ExternalBankService {

    private final MockBankClient mockBankClient;
    private final UserService userService;

    public List<LinkableAccountResponse> fetchAvailableAccountsFromBank(String userToken) {
        String userKey = userService.getUserKeyByUserToken(userToken);

        return mockBankClient.getAccountsByUserKey(userKey);
    }

    public AccountDetailResponse getAccountDetail(Long accountId, String userKey) {
        return mockBankClient.getAccountDetail(accountId, userKey);
    }
}

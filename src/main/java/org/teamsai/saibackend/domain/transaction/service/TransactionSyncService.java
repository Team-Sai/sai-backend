package org.teamsai.saibackend.domain.transaction.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.teamsai.saibackend.domain.account.dto.LinkedBankAccountDTO;
import org.teamsai.saibackend.domain.account.exception.AccountErrorCode;
import org.teamsai.saibackend.domain.account.mapper.LinkedBankAccountMapper;
import org.teamsai.saibackend.domain.transaction.dto.response.BankTransactionResponse;
import org.teamsai.saibackend.domain.user.service.UserService;
import org.teamsai.saibackend.global.client.MockBankClient;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransactionSyncService {

    private final LinkedBankAccountMapper linkedBankAccountMapper;
    private final MockBankClient mockBankClient;
    private final UserService userService;
    private final BankTransactionPersistenceService bankTransactionPersistenceService;

    /**
     * 이번 동기화 요청에서 처리한 거래 건수를 반환한다.
     * (신규 저장 건수를 의미하지 않는다.
    **/

    public int syncTransactions(Long userId, Long linkedAccountId) {
        LinkedBankAccountDTO linkedAccount = linkedBankAccountMapper.findById(linkedAccountId)
                .orElseThrow(AccountErrorCode.LINKED_ACCOUNT_NOT_FOUND::toException);

        validateOwnership(userId, linkedAccount);

        String userKey = userService.getUserKeyByUserId(linkedAccount.getUserId());

        Long lastSyncedId = linkedBankAccountMapper.
                            findLastSyncedTransactionIdById(linkedAccountId);
        long afterTransactionId = lastSyncedId == null ? 0L : lastSyncedId;

        List<BankTransactionResponse> transactions = fetchTransactions(
                linkedAccountId,
                linkedAccount.getAccountId(),
                userKey,
                afterTransactionId
        );

        return bankTransactionPersistenceService.
                saveAndAdvanceCursor(linkedAccountId, transactions);
    }

    private void validateOwnership(Long userId, LinkedBankAccountDTO linkedAccount) {
        if (!linkedAccount.getUserId().equals(userId)) {
            log.warn(
                    "[TransactionSyncService] 소유자가 아닌 계좌 동기화 시도 - " +
                            "requesterId: {}, linkedAccountId: {}",
                    userId, linkedAccount.getLinkedAccountId()
            );
            throw AccountErrorCode.ACCOUNT_ACCESS_DENIED.toException();
        }
    }

    private List<BankTransactionResponse> fetchTransactions(
            Long linkedAccountId,
            Long bankAccountId,
            String userKey,
            long afterTransactionId
    ) {
        try {
            return mockBankClient.
                    getTransactions(bankAccountId, userKey, afterTransactionId);
        } catch (RestClientException e) {
            log.warn("[TransactionSyncService] 거래내역 조회 실패 - linkedAccountId: {}",
                    linkedAccountId, e);
            throw AccountErrorCode.BANK_SERVER_UNAVAILABLE.toException();
        }
    }
}
 
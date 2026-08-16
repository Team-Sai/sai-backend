package org.teamsai.saibackend.domain.transaction.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.teamsai.saibackend.domain.account.dto.LinkedBankAccountDTO;
import org.teamsai.saibackend.domain.account.exception.AccountErrorCode;
import org.teamsai.saibackend.domain.account.mapper.LinkedBankAccountMapper;
import org.teamsai.saibackend.domain.transaction.dto.BankTransactionDTO;
import org.teamsai.saibackend.domain.transaction.dto.request.BankTransactionSearchCondition;
import org.teamsai.saibackend.domain.transaction.dto.response.BankTransactionDetailResponse;
import org.teamsai.saibackend.domain.transaction.dto.response.BankTransactionListItemResponse;
import org.teamsai.saibackend.domain.transaction.dto.response.PageResponse;
import org.teamsai.saibackend.domain.transaction.exception.BankTransactionErrorCode;
import org.teamsai.saibackend.domain.transaction.mapper.BankTransactionMapper;

import java.util.List;

@Service
@RequiredArgsConstructor
public class BankTransactionQueryService {

    private final BankTransactionMapper bankTransactionMapper;
    private final LinkedBankAccountMapper linkedBankAccountMapper;

    @Transactional(readOnly = true)
    public PageResponse<BankTransactionListItemResponse> getTransactions(
            Long userId, Long linkedAccountId, BankTransactionSearchCondition condition
    ) {
        validateOwnership(userId, linkedAccountId);

        List<BankTransactionDTO> transactions = bankTransactionMapper.search(linkedAccountId, condition);
        long totalCount = bankTransactionMapper.countBySearch(linkedAccountId, condition);

        List<BankTransactionListItemResponse> content = transactions.stream()
                .map(BankTransactionListItemResponse::from)
                .toList();

        return PageResponse.of(content, condition.page(), condition.size(), totalCount);
    }

    @Transactional(readOnly = true)
    public BankTransactionDetailResponse getTransactionDetail(
            Long userId, Long linkedAccountId, Long bankTransactionId
    ) {
        validateOwnership(userId, linkedAccountId);

        BankTransactionDTO transaction = bankTransactionMapper
                .findByIdAndLinkedAccountId(bankTransactionId, linkedAccountId)
                .orElseThrow(BankTransactionErrorCode.BANK_TRANSACTION_NOT_FOUND::toException);

        return BankTransactionDetailResponse.from(transaction);
    }

    @Transactional
    public BankTransactionDetailResponse getTransactionDetailForUpdate(
            Long userId,
            Long linkedAccountId,
            Long bankTransactionId
    ) {
        validateOwnership(userId, linkedAccountId);

        BankTransactionDTO transaction = bankTransactionMapper
                .findByIdAndLinkedAccountIdForUpdate(
                        bankTransactionId,
                        linkedAccountId
                )
                .orElseThrow(
                        BankTransactionErrorCode
                                .BANK_TRANSACTION_NOT_FOUND
                                ::toException
                );

        return BankTransactionDetailResponse.from(transaction);
    }

    private void validateOwnership(Long userId, Long linkedAccountId) {
        LinkedBankAccountDTO linkedAccount = linkedBankAccountMapper.findById(linkedAccountId)
                .orElseThrow(AccountErrorCode.LINKED_ACCOUNT_NOT_FOUND::toException);

        if (!linkedAccount.getUserId().equals(userId)) {
            throw AccountErrorCode.ACCOUNT_ACCESS_DENIED.toException();
        }
    }
}

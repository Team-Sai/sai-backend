package org.teamsai.saibackend.domain.settlement.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.teamsai.saibackend.domain.account.dto.response.LinkedBankAccountResponse;
import org.teamsai.saibackend.domain.account.service.LinkedBankAccountService;
import org.teamsai.saibackend.domain.settlement.dto.SettlementAccountDTO;
import org.teamsai.saibackend.domain.settlement.dto.SettlementDTO;
import org.teamsai.saibackend.domain.settlement.dto.response.SettlementAccountResponse;
import org.teamsai.saibackend.domain.settlement.exception.SettlementErrorCode;
import org.teamsai.saibackend.domain.settlement.mapper.SettlementAccountMapper;
import org.teamsai.saibackend.domain.settlement.mapper.SettlementMapper;
import org.teamsai.saibackend.domain.settlement.type.SettlementAccountStatus;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Optional;
@Slf4j
@Service
@RequiredArgsConstructor
public class SettlementAccountService {

    private final SettlementMapper settlementMapper;
    private final SettlementAccountMapper settlementAccountMapper;
    private final LinkedBankAccountService linkedBankAccountService;

    @Transactional
    public SettlementAccountResponse selectAccount(
            Long userId,
            Long settlementId,
            Long linkedAccountId
    ) {
        log.info(
                "[정산계좌] 설정 시작 userId={}, settlementId={}, linkedAccountId={}",
                userId,
                settlementId,
                linkedAccountId
        );

        SettlementDTO settlement = findSettlement(settlementId);

        validatorOwner(
                settlement,
                userId
        );

        validateLinkedAccountOwner(
                userId,
                linkedAccountId
        );

        Optional<SettlementAccountDTO> currentAccount =
                settlementAccountMapper.findActiveBySettlementIdForUpdate(
                        settlementId
                );

        log.info(
                "[정산계좌] 기존 ACTIVE 계좌 존재={}",
                currentAccount.isPresent()
        );

        if (currentAccount.isPresent()
                && Objects.equals(
                currentAccount.get().getLinkedAccountId(),
                linkedAccountId
        )) {

            log.info(
                    "[정산계좌] 이미 동일 계좌 설정됨 settlementId={}",
                    settlementId
            );

            return toResponse(
                    userId,
                    currentAccount.get()
            );
        }

        LocalDateTime now = LocalDateTime.now();

        currentAccount.ifPresent(
                account -> replaceCurrentAccount(
                        account,
                        now
                )
        );

        SettlementAccountDTO newAccount =
                SettlementAccountDTO.builder()
                        .settlementId(settlementId)
                        .linkedAccountId(linkedAccountId)
                        .accountStatus(
                                SettlementAccountStatus.ACTIVE
                        )
                        .selectedAt(now)
                        .endedAt(null)
                        .build();

        log.info(
                "[정산계좌] INSERT 직전 settlementId={}, linkedAccountId={}, status={}",
                newAccount.getSettlementId(),
                newAccount.getLinkedAccountId(),
                newAccount.getAccountStatus()
        );

        int insertedCount =
                settlementAccountMapper.insert(
                        newAccount
                );

        log.info(
                "[정산계좌] INSERT 결과 count={}, generatedId={}",
                insertedCount,
                newAccount.getSettlementAccountId()
        );

        if (insertedCount != 1) {
            throw SettlementErrorCode
                    .SETTLEMENT_ACCOUNT_CREATE_FAILED
                    .toException();
        }

        return toResponse(
                userId,
                newAccount
        );
    }

    private SettlementAccountResponse toResponse(
            Long userId,
            SettlementAccountDTO settlementAccount
    ) {
        LinkedBankAccountResponse linkedAccount =
                linkedBankAccountService
                        .getLinkedAccounts(userId)
                        .stream()
                        .filter(account ->
                                Objects.equals(
                                        account.linkedAccountId(),
                                        settlementAccount.getLinkedAccountId()
                                )
                        )
                        .findFirst()
                        .orElseThrow(
                                SettlementErrorCode
                                        .INVALID_SETTLEMENT_ACCOUNT
                                        ::toException
                        );

        return SettlementAccountResponse.from(
                settlementAccount,
                linkedAccount
        );
    }

    private void validateLinkedAccountOwner(Long userId, Long linkedAccountId
    ) {
        if (!linkedBankAccountService.isOwnedLinkedAccount(userId, linkedAccountId)) {
            throw SettlementErrorCode.INVALID_SETTLEMENT_ACCOUNT.toException();
        }
    }

    private void validatorOwner(SettlementDTO settlement, Long userId) {
        if(!settlement.getOwnerId().equals(userId)){
            throw SettlementErrorCode.SETTLEMENT_ACCESS_DENIED.toException();
        }
    }

    @Transactional(readOnly = true)
    public SettlementAccountResponse findCurrentAccount(Long userId, Long settlementId){
        SettlementDTO settlement = findSettlement(settlementId);

        validatorOwner(settlement,userId);

        SettlementAccountDTO account = settlementAccountMapper.findActiveBySettlementId(settlementId)
                .orElseThrow(SettlementErrorCode.SETTLEMENT_ACCOUNT_NOT_FOUND::toException);

        return toResponse(userId, account);
    }


    private SettlementDTO findSettlement(Long settlementId){
        return settlementMapper.findById(settlementId).orElseThrow(SettlementErrorCode.SETTLEMENT_NOT_FOUND::toException);
    }

    private void replaceCurrentAccount(SettlementAccountDTO account, LocalDateTime endedAt){
        int updatedCount = settlementAccountMapper.updateStatus(account.getSettlementAccountId(), SettlementAccountStatus.REPLACED,endedAt);

        if(updatedCount != 1){
            throw SettlementErrorCode.SETTLEMENT_ACCOUNT_UPDATE_FAILED.toException();
        }
    }
}

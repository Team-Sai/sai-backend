package org.teamsai.saibackend.domain.settlement.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.teamsai.saibackend.domain.account.service.LinkedBankAccountService;
import org.teamsai.saibackend.domain.settlement.dto.SettlementAccountDTO;
import org.teamsai.saibackend.domain.settlement.dto.SettlementDTO;
import org.teamsai.saibackend.domain.settlement.dto.request.SelectSettlementAccountRequest;
import org.teamsai.saibackend.domain.settlement.dto.response.SettlementAccountResponse;
import org.teamsai.saibackend.domain.settlement.exception.SettlementErrorCode;
import org.teamsai.saibackend.domain.settlement.mapper.SettlementAccountMapper;
import org.teamsai.saibackend.domain.settlement.mapper.SettlementMapper;
import org.teamsai.saibackend.domain.settlement.type.SettlementAccountStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class SettlementAccountService {

    private final SettlementMapper settlementMapper;
    private final SettlementAccountMapper settlementAccountMapper;
    private final LinkedBankAccountService linkedBankAccountService;

    @Transactional
    public SettlementAccountResponse selectAccount(Long userId, Long settlementId, SelectSettlementAccountRequest request){

        SettlementDTO settlement = findSettlement(settlementId);

        validatorOwner(settlement, userId);
        validateLinkedAccountOwner(userId, request.getLinkedAccountId());

        Optional<SettlementAccountDTO> currentAccount = settlementAccountMapper.findActiveBySettlementIdForUpdate(settlementId);

        if(currentAccount.isPresent() && currentAccount.get().getLinkedAccountId().equals(request.getLinkedAccountId())){
            return SettlementAccountResponse.from(currentAccount.get());
        }

        LocalDateTime now = LocalDateTime.now();

        currentAccount.ifPresent(account-> replaceCurrentAccount(account,now));

        SettlementAccountDTO newAccount = SettlementAccountDTO.builder()
                .settlementId(settlementId)
                .linkedAccountId(request.getLinkedAccountId())
                .accountStatus(SettlementAccountStatus.ACTIVE)
                .selectedAt(now)
                .endedAt(null)
                .build();

        int insertedCount = settlementAccountMapper.insert(newAccount);

        if(insertedCount != 1){
            throw SettlementErrorCode.SETTLEMENT_ACCOUNT_CREATE_FAILED.toException();
        }

        return SettlementAccountResponse.from(newAccount);

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

        return SettlementAccountResponse.from(account);
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

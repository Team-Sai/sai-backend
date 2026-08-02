package org.teamsai.saibackend.domain.account.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.teamsai.saibackend.domain.account.dto.*;
import org.teamsai.saibackend.domain.account.mapper.LinkedBankAccountMapper;
import org.teamsai.saibackend.global.client.MockBankClient;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class LinkedBankAccountService {

    private final LinkedBankAccountMapper linkedBankAccountMapper;

    private static final String DEFAULT_BANK_CODE = "SAI_001";

    @Transactional
    public List<LinkedBankAccountResponse> linkSelectedAccounts(Long userId, LinkAccountRequest request) {
        log.info("[LinkedBankAccountService] 계좌 연동 시작 - userId: {}, 선택된 계좌 수: {}",
                userId, request.selectedAccounts().size());

        List<LinkedBankAccountDTO> dtosToSave = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();

        for (LinkAccountRequest.SelectedAccount selected : request.selectedAccounts()) {
            //String maskedNumber = MaskingUtil.maskAccountNumber(selected.accountNumber());

            LinkedBankAccountDTO dto = LinkedBankAccountDTO.builder()
                    .userId(userId)
                    .bankCode(DEFAULT_BANK_CODE)
                    .accountNumber(selected.accountNumber())
                    .accountAlias(selected.accountAlias())
                    .accountHolderName(selected.accountHolderName())
                    .connectionStatus(ConnectionStatus.AVAILABLE)
                    .createdAt(now)
                    .updatedAt(now)
                    .build();

            dtosToSave.add(dto);
        }

        linkedBankAccountMapper.insertBatch(dtosToSave);

        return dtosToSave.stream()
                .map(LinkedBankAccountResponse::from)
                .toList();
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

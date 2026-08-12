package org.teamsai.saibackend.domain.settlement.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.teamsai.saibackend.domain.account.service.LinkedBankAccountService;
import org.teamsai.saibackend.domain.settlement.dto.SettlementDTO;
import org.teamsai.saibackend.domain.settlement.dto.request.CreateSettlementParticipantRequest;
import org.teamsai.saibackend.domain.settlement.dto.request.CreateSharedSettlementRequest;
import org.teamsai.saibackend.domain.settlement.exception.SettlementErrorCode;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class SettlementValidator {

    private final LinkedBankAccountService linkedBankAccountService;

    public void validateOwner(SettlementDTO settlement, Long userId){
        if(!settlement.getOwnerId().equals(userId)){
            throw SettlementErrorCode.SETTLEMENT_ACCESS_DENIED.toException();
        }
    }

    public void validateLinkedAccountOwner(Long userId, Long linkedAccountId
    ) {
        if (!linkedBankAccountService.isOwnedLinkedAccount(userId, linkedAccountId)) {
            throw SettlementErrorCode.INVALID_SETTLEMENT_ACCOUNT.toException();
        }
    }

    public void validateCreateRequest(CreateSharedSettlementRequest request) {
        if (request == null) {
            throw SettlementErrorCode
                    .INVALID_SETTLEMENT_REQUEST
                    .toException();
        }

        List<CreateSettlementParticipantRequest> participants =
                request.getParticipants();

        if (participants == null || participants.isEmpty()) {
            throw SettlementErrorCode
                    .SETTLEMENT_PARTICIPANT_REQUIRED
                    .toException();
        }

        validateDuplicateParticipants(participants);
    }

    private void validateDuplicateParticipants(List<CreateSettlementParticipantRequest> participants) {
        Set<String> userTokens = new HashSet<>();

        for (CreateSettlementParticipantRequest participant
                : participants) {

            if (participant == null
                    || participant.getUserToken() == null
                    || participant.getUserToken().isBlank()) {
                throw SettlementErrorCode
                        .INVALID_SETTLEMENT_PARTICIPANT
                        .toException();
            }

            if (!userTokens.add(participant.getUserToken())) {
                throw SettlementErrorCode
                        .DUPLICATE_SETTLEMENT_PARTICIPANT
                        .toException();
            }
        }
    }
}

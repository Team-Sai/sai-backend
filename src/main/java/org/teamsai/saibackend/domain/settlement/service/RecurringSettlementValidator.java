package org.teamsai.saibackend.domain.settlement.service;

import org.springframework.stereotype.Component;
import org.teamsai.saibackend.domain.settlement.dto.request.CreateRecurringSettlementRequest;
import org.teamsai.saibackend.domain.settlement.dto.request.CreateSettlementParticipantRequest;
import org.teamsai.saibackend.domain.settlement.exception.SettlementErrorCode;

import java.util.HashSet;
import java.util.Set;

@Component
public class RecurringSettlementValidator {

    public void validateCreateRequest(CreateRecurringSettlementRequest request){
        if(request == null){
            throw SettlementErrorCode.INVALID_SETTLEMENT_REQUEST.toException();
        }

        Set<String> userTokens = new HashSet<>();

        for (CreateSettlementParticipantRequest participant : request.getParticipants()) {
            if (
                    participant == null ||
                            participant.getUserToken() == null ||
                            participant.getUserToken().isBlank()
            ) {
                throw SettlementErrorCode.INVALID_SETTLEMENT_PARTICIPANT.toException();
            }

            if (!userTokens.add(participant.getUserToken())) {
                throw SettlementErrorCode.DUPLICATE_SETTLEMENT_PARTICIPANT.toException();
            }
        }
    }
}

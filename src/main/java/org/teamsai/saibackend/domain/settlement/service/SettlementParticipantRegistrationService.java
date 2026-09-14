package org.teamsai.saibackend.domain.settlement.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.teamsai.saibackend.domain.notification.service.NotificationService;
import org.teamsai.saibackend.domain.notification.type.NotificationType;
import org.teamsai.saibackend.domain.payment.service.SettlementPaymentService;
import org.teamsai.saibackend.domain.settlement.dto.request.CreateSettlementParticipantRequest;
import org.teamsai.saibackend.domain.user.dto.UserDTO;
import org.teamsai.saibackend.domain.user.service.UserService;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SettlementParticipantRegistrationService {

    private final UserService userService;
    private final SettlementParticipantService participantService;
    private final SettlementPaymentService settlementPaymentService;
    private final NotificationService notificationService;



    public void registerParticipants(
            Long ownerId,
            Long settlementId,
            List<CreateSettlementParticipantRequest> participants,
            BigDecimal expectedAmount
    ) {
        for (CreateSettlementParticipantRequest participantRequest
                : participants) {

            UserDTO participantUser =
                    userService.findRequestTarget(
                            ownerId,
                            participantRequest.getUserToken()
                    );

            Long participantId =
                    participantService.createParticipant(
                            settlementId,
                            participantUser.getUserId()
                    );

            settlementPaymentService.createObligation(
                    participantId,
                    expectedAmount
            );
            notificationService.create(
                    participantUser.getUserId(),
                    NotificationType.SETTLEMENT_PARTICIPANT_ADDED,
                    "새로운 정산에 참여자로 등록되었습니다.",
                    "정산 금액 "
                            + expectedAmount.toPlainString()
                            + "원이 등록되었습니다.",
                    settlementId
            );
        }
    }
}

package org.teamsai.saibackend.domain.settlement;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.teamsai.saibackend.domain.notification.service.NotificationService;
import org.teamsai.saibackend.domain.notification.type.NotificationType;
import org.teamsai.saibackend.domain.payment.service.SettlementPaymentService;
import org.teamsai.saibackend.domain.settlement.dto.request.CreateSettlementParticipantRequest;
import org.teamsai.saibackend.domain.settlement.service.SettlementParticipantRegistrationService;
import org.teamsai.saibackend.domain.settlement.service.SettlementParticipantService;
import org.teamsai.saibackend.domain.user.dto.UserDTO;
import org.teamsai.saibackend.domain.user.service.UserService;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("SettlementParticipantRegistrationService 단위 테스트")
class SettlementParticipantRegistrationServiceTest {

    private static final Long OWNER_ID = 1L;
    private static final Long SETTLEMENT_ID = 10L;

    private static final Long FIRST_USER_ID = 2L;
    private static final Long SECOND_USER_ID = 3L;

    private static final Long FIRST_PARTICIPANT_ID = 100L;
    private static final Long SECOND_PARTICIPANT_ID = 101L;

    private static final String FIRST_USER_TOKEN =
            "SAI_USER_A";

    private static final String SECOND_USER_TOKEN =
            "SAI_USER_B";

    private static final BigDecimal EXPECTED_AMOUNT =
            new BigDecimal("150000");


    @Mock
    private UserService userService;

    @Mock
    private SettlementParticipantService participantService;

    @Mock
    private SettlementPaymentService settlementPaymentService;

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private SettlementParticipantRegistrationService
            participantRegistrationService;


    @Test
    @DisplayName(
            "참여자를 등록하면 사용자 조회 후 참여자와 납부 의무를 생성하고 알림을 전송한다"
    )
    void registerParticipantsSuccess() {

        List<CreateSettlementParticipantRequest> participants =
                List.of(
                        participant(FIRST_USER_TOKEN),
                        participant(SECOND_USER_TOKEN)
                );


        UserDTO firstUser =
                UserDTO.builder()
                        .userId(FIRST_USER_ID)
                        .userToken(FIRST_USER_TOKEN)
                        .build();

        UserDTO secondUser =
                UserDTO.builder()
                        .userId(SECOND_USER_ID)
                        .userToken(SECOND_USER_TOKEN)
                        .build();


        given(
                userService.findRequestTarget(
                        OWNER_ID,
                        FIRST_USER_TOKEN
                )
        ).willReturn(firstUser);

        given(
                userService.findRequestTarget(
                        OWNER_ID,
                        SECOND_USER_TOKEN
                )
        ).willReturn(secondUser);


        given(
                participantService.createParticipant(
                        SETTLEMENT_ID,
                        FIRST_USER_ID
                )
        ).willReturn(FIRST_PARTICIPANT_ID);

        given(
                participantService.createParticipant(
                        SETTLEMENT_ID,
                        SECOND_USER_ID
                )
        ).willReturn(SECOND_PARTICIPANT_ID);


        participantRegistrationService.registerParticipants(
                OWNER_ID,
                SETTLEMENT_ID,
                participants,
                EXPECTED_AMOUNT
        );


        verify(userService)
                .findRequestTarget(
                        OWNER_ID,
                        FIRST_USER_TOKEN
                );

        verify(userService)
                .findRequestTarget(
                        OWNER_ID,
                        SECOND_USER_TOKEN
                );


        verify(participantService)
                .createParticipant(
                        SETTLEMENT_ID,
                        FIRST_USER_ID
                );

        verify(participantService)
                .createParticipant(
                        SETTLEMENT_ID,
                        SECOND_USER_ID
                );


        verify(settlementPaymentService)
                .createObligation(
                        FIRST_PARTICIPANT_ID,
                        EXPECTED_AMOUNT
                );

        verify(settlementPaymentService)
                .createObligation(
                        SECOND_PARTICIPANT_ID,
                        EXPECTED_AMOUNT
                );


        verify(notificationService)
                .create(
                        FIRST_USER_ID,
                        NotificationType.SETTLEMENT_PARTICIPANT_ADDED,
                        "새로운 정산에 참여자로 등록되었습니다.",
                        "정산 금액 "
                                + EXPECTED_AMOUNT.toPlainString()
                                + "원이 등록되었습니다.",
                        SETTLEMENT_ID
                );

        verify(notificationService)
                .create(
                        SECOND_USER_ID,
                        NotificationType.SETTLEMENT_PARTICIPANT_ADDED,
                        "새로운 정산에 참여자로 등록되었습니다.",
                        "정산 금액 "
                                + EXPECTED_AMOUNT.toPlainString()
                                + "원이 등록되었습니다.",
                        SETTLEMENT_ID
                );
    }


    @Test
    @DisplayName(
            "참여자 등록은 사용자 조회, 참여자 생성, 납부 의무 생성, 알림 순서로 처리한다"
    )
    void registerParticipantInOrder() {

        List<CreateSettlementParticipantRequest> participants =
                List.of(
                        participant(FIRST_USER_TOKEN)
                );


        UserDTO participantUser =
                UserDTO.builder()
                        .userId(FIRST_USER_ID)
                        .userToken(FIRST_USER_TOKEN)
                        .build();


        given(
                userService.findRequestTarget(
                        OWNER_ID,
                        FIRST_USER_TOKEN
                )
        ).willReturn(participantUser);

        given(
                participantService.createParticipant(
                        SETTLEMENT_ID,
                        FIRST_USER_ID
                )
        ).willReturn(FIRST_PARTICIPANT_ID);


        participantRegistrationService.registerParticipants(
                OWNER_ID,
                SETTLEMENT_ID,
                participants,
                EXPECTED_AMOUNT
        );


        InOrder inOrder =
                inOrder(
                        userService,
                        participantService,
                        settlementPaymentService,
                        notificationService
                );


        inOrder.verify(userService)
                .findRequestTarget(
                        OWNER_ID,
                        FIRST_USER_TOKEN
                );

        inOrder.verify(participantService)
                .createParticipant(
                        SETTLEMENT_ID,
                        FIRST_USER_ID
                );

        inOrder.verify(settlementPaymentService)
                .createObligation(
                        FIRST_PARTICIPANT_ID,
                        EXPECTED_AMOUNT
                );

        inOrder.verify(notificationService)
                .create(
                        FIRST_USER_ID,
                        NotificationType.SETTLEMENT_PARTICIPANT_ADDED,
                        "새로운 정산에 참여자로 등록되었습니다.",
                        "정산 금액 "
                                + EXPECTED_AMOUNT.toPlainString()
                                + "원이 등록되었습니다.",
                        SETTLEMENT_ID
                );
    }


    private CreateSettlementParticipantRequest participant(
            String userToken
    ) {

        return CreateSettlementParticipantRequest.builder()
                .userToken(userToken)
                .build();
    }
}
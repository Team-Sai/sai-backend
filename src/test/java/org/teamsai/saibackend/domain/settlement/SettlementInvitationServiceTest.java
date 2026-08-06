package org.teamsai.saibackend.domain.settlement;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.teamsai.saibackend.domain.payment.service.PaymentService;
import org.teamsai.saibackend.domain.settlement.dto.SettlementDTO;
import org.teamsai.saibackend.domain.settlement.dto.SettlementInvitationDTO;
import org.teamsai.saibackend.domain.settlement.dto.request.CreateSettlementInvitationRequest;
import org.teamsai.saibackend.domain.settlement.dto.response.ReceivedSettlementInvitationResponse;
import org.teamsai.saibackend.domain.settlement.mapper.SettlementInvitationMapper;
import org.teamsai.saibackend.domain.settlement.mapper.SettlementMapper;
import org.teamsai.saibackend.domain.settlement.service.SettlementInvitationService;
import org.teamsai.saibackend.domain.settlement.service.SettlementInvitationValidator;
import org.teamsai.saibackend.domain.settlement.service.SettlementParticipantService;
import org.teamsai.saibackend.domain.settlement.type.SettlementInvitationStatus;
import org.teamsai.saibackend.domain.user.dto.UserDTO;
import org.teamsai.saibackend.domain.user.service.UserService;
import org.teamsai.saibackend.global.exception.DomainException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("SettlementInvitationService 단위 테스트")
class SettlementInvitationServiceTest {

    private static final Long OWNER_ID = 1L;
    private static final Long SETTLEMENT_ID = 10L;

    private static final Long FIRST_INVITED_USER_ID = 2L;
    private static final Long SECOND_INVITED_USER_ID = 3L;

    private static final Long FIRST_INVITATION_ID = 100L;
    private static final Long SECOND_INVITATION_ID = 101L;

    private static final Long FIRST_PARTICIPANT_ID = 200L;
    private static final Long SECOND_PARTICIPANT_ID = 201L;

    private static final String FIRST_USER_TOKEN =
            "target-user-token-1";

    private static final String SECOND_USER_TOKEN =
            "target-user-token-2";

    private static final BigDecimal EXPECTED_AMOUNT =
            new BigDecimal("150000");

    @Mock
    private SettlementMapper settlementMapper;

    @Mock
    private SettlementInvitationMapper invitationMapper;

    @Mock
    private UserService userService;

    @Mock
    private SettlementInvitationValidator invitationValidator;

    @Mock
    private SettlementParticipantService participantService;

    @Mock
    private PaymentService paymentService;

    @InjectMocks
    private SettlementInvitationService settlementInvitationService;

    @Test
    @DisplayName(
            "정산 생성 시 납부자별 초대, 참여자, 납부 의무를 생성한다"
    )
    void inviteAllSuccess() {
        SettlementDTO settlement =
                SettlementDTO.builder()
                        .settlementId(SETTLEMENT_ID)
                        .ownerId(OWNER_ID)
                        .build();

        UserDTO firstInvitedUser = createUser(
                FIRST_INVITED_USER_ID,
                FIRST_USER_TOKEN,
                "첫 번째 회원"
        );

        UserDTO secondInvitedUser = createUser(
                SECOND_INVITED_USER_ID,
                SECOND_USER_TOKEN,
                "두 번째 회원"
        );

        CreateSettlementInvitationRequest firstRequest =
                createInvitationRequest(FIRST_USER_TOKEN);

        CreateSettlementInvitationRequest secondRequest =
                createInvitationRequest(SECOND_USER_TOKEN);

        List<CreateSettlementInvitationRequest> requests =
                List.of(
                        firstRequest,
                        secondRequest
                );

        when(settlementMapper.findById(SETTLEMENT_ID))
                .thenReturn(Optional.of(settlement));

        when(userService.findRequestTarget(
                OWNER_ID,
                FIRST_USER_TOKEN
        )).thenReturn(firstInvitedUser);

        when(userService.findRequestTarget(
                OWNER_ID,
                SECOND_USER_TOKEN
        )).thenReturn(secondInvitedUser);

        AtomicLong invitationIdSequence =
                new AtomicLong(FIRST_INVITATION_ID);

        when(invitationMapper.insert(
                any(SettlementInvitationDTO.class)
        )).thenAnswer(invocation -> {
            SettlementInvitationDTO invitation =
                    invocation.getArgument(0);

            ReflectionTestUtils.setField(
                    invitation,
                    "invitationId",
                    invitationIdSequence.getAndIncrement()
            );

            return 1;
        });

        when(participantService.createFromInvitation(
                FIRST_INVITATION_ID
        )).thenReturn(FIRST_PARTICIPANT_ID);

        when(participantService.createFromInvitation(
                SECOND_INVITATION_ID
        )).thenReturn(SECOND_PARTICIPANT_ID);

        settlementInvitationService.inviteAll(
                OWNER_ID,
                SETTLEMENT_ID,
                requests,
                EXPECTED_AMOUNT
        );

        verify(invitationValidator)
                .validateInvitableSettlement(
                        settlement,
                        OWNER_ID
                );

        verify(invitationValidator)
                .validateInviteTarget(
                        SETTLEMENT_ID,
                        FIRST_INVITED_USER_ID
                );

        verify(invitationValidator)
                .validateInviteTarget(
                        SETTLEMENT_ID,
                        SECOND_INVITED_USER_ID
                );

        ArgumentCaptor<SettlementInvitationDTO> invitationCaptor =
                ArgumentCaptor.forClass(
                        SettlementInvitationDTO.class
                );

        verify(invitationMapper,
                org.mockito.Mockito.times(2))
                .insert(invitationCaptor.capture());

        List<SettlementInvitationDTO> savedInvitations =
                invitationCaptor.getAllValues();

        assertThat(savedInvitations)
                .hasSize(2);

        SettlementInvitationDTO firstInvitation =
                savedInvitations.get(0);

        assertThat(firstInvitation.getSettlementId())
                .isEqualTo(SETTLEMENT_ID);

        assertThat(firstInvitation.getInvitedUserId())
                .isEqualTo(FIRST_INVITED_USER_ID);

        assertThat(firstInvitation.getInvitationStatus())
                .isEqualTo(SettlementInvitationStatus.INVITED);

        assertThat(firstInvitation.getInvitedAt())
                .isNotNull();

        assertThat(firstInvitation.getAcceptedAt())
                .isNull();

        SettlementInvitationDTO secondInvitation =
                savedInvitations.get(1);

        assertThat(secondInvitation.getSettlementId())
                .isEqualTo(SETTLEMENT_ID);

        assertThat(secondInvitation.getInvitedUserId())
                .isEqualTo(SECOND_INVITED_USER_ID);

        assertThat(secondInvitation.getInvitationStatus())
                .isEqualTo(SettlementInvitationStatus.INVITED);

        assertThat(secondInvitation.getInvitedAt())
                .isNotNull();

        assertThat(secondInvitation.getAcceptedAt())
                .isNull();

        verify(participantService)
                .createFromInvitation(
                        FIRST_INVITATION_ID
                );

        verify(participantService)
                .createFromInvitation(
                        SECOND_INVITATION_ID
                );

        verify(paymentService)
                .createObligation(
                        FIRST_PARTICIPANT_ID,
                        EXPECTED_AMOUNT
                );

        verify(paymentService)
                .createObligation(
                        SECOND_PARTICIPANT_ID,
                        EXPECTED_AMOUNT
                );
    }

    @Test
    @DisplayName("존재하지 않는 정산에는 일괄 초대를 생성할 수 없다")
    void inviteAllFailWhenSettlementNotFound() {
        CreateSettlementInvitationRequest request =
                mock(CreateSettlementInvitationRequest.class);

        when(settlementMapper.findById(SETTLEMENT_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(
                () -> settlementInvitationService.inviteAll(
                        OWNER_ID,
                        SETTLEMENT_ID,
                        List.of(request),
                        EXPECTED_AMOUNT
                )
        ).isInstanceOf(DomainException.class);

        verifyNoInteractions(
                invitationValidator,
                userService,
                invitationMapper,
                participantService,
                paymentService
        );
    }

    @Test
    @DisplayName(
            "초대 저장에 실패하면 참여자와 납부 의무를 생성하지 않는다"
    )
    void inviteAllFailWhenInvitationInsertFailed() {
        SettlementDTO settlement =
                SettlementDTO.builder()
                        .settlementId(SETTLEMENT_ID)
                        .ownerId(OWNER_ID)
                        .build();

        UserDTO invitedUser = mock(UserDTO.class);

        when(invitedUser.getUserId())
                .thenReturn(FIRST_INVITED_USER_ID);

        CreateSettlementInvitationRequest request =
                createInvitationRequest(FIRST_USER_TOKEN);

        when(settlementMapper.findById(SETTLEMENT_ID))
                .thenReturn(Optional.of(settlement));

        when(userService.findRequestTarget(
                OWNER_ID,
                FIRST_USER_TOKEN
        )).thenReturn(invitedUser);

        when(invitationMapper.insert(
                any(SettlementInvitationDTO.class)
        )).thenReturn(0);

        assertThatThrownBy(
                () -> settlementInvitationService.inviteAll(
                        OWNER_ID,
                        SETTLEMENT_ID,
                        List.of(request),
                        EXPECTED_AMOUNT
                )
        ).isInstanceOf(DomainException.class);

        verify(invitationMapper)
                .insert(any(SettlementInvitationDTO.class));

        verifyNoInteractions(
                participantService,
                paymentService
        );
    }

    @Test
    @DisplayName(
            "참여자 생성 후 동일한 예정 원금으로 납부 의무를 생성한다"
    )
    void inviteAllCreatesObligationWithExpectedAmount() {
        SettlementDTO settlement =
                SettlementDTO.builder()
                        .settlementId(SETTLEMENT_ID)
                        .ownerId(OWNER_ID)
                        .build();

        UserDTO invitedUser = createUser(
                FIRST_INVITED_USER_ID,
                FIRST_USER_TOKEN,
                "초대 회원"
        );

        CreateSettlementInvitationRequest request =
                createInvitationRequest(FIRST_USER_TOKEN);

        when(settlementMapper.findById(SETTLEMENT_ID))
                .thenReturn(Optional.of(settlement));

        when(userService.findRequestTarget(
                OWNER_ID,
                FIRST_USER_TOKEN
        )).thenReturn(invitedUser);

        when(invitationMapper.insert(
                any(SettlementInvitationDTO.class)
        )).thenAnswer(invocation -> {
            SettlementInvitationDTO invitation =
                    invocation.getArgument(0);

            ReflectionTestUtils.setField(
                    invitation,
                    "invitationId",
                    FIRST_INVITATION_ID
            );

            return 1;
        });

        when(participantService.createFromInvitation(
                FIRST_INVITATION_ID
        )).thenReturn(FIRST_PARTICIPANT_ID);

        settlementInvitationService.inviteAll(
                OWNER_ID,
                SETTLEMENT_ID,
                List.of(request),
                EXPECTED_AMOUNT
        );

        verify(paymentService)
                .createObligation(
                        FIRST_PARTICIPANT_ID,
                        EXPECTED_AMOUNT
                );
    }

    @Test
    @DisplayName("로그인한 회원이 받은 정산 초대 목록을 조회한다")
    void findReceivedInvitationsSuccess() {
        Long userId = 2L;

        ReceivedSettlementInvitationResponse firstInvitation =
                ReceivedSettlementInvitationResponse.builder()
                        .invitationId(3L)
                        .settlementId(2L)
                        .settlementTitle("제주도")
                        .ownerName("김사이")
                        .invitationStatus(
                                SettlementInvitationStatus.INVITED
                        )
                        .invitedAt(
                                LocalDateTime.of(
                                        2026,
                                        8,
                                        4,
                                        16,
                                        0,
                                        31
                                )
                        )
                        .acceptedAt(null)
                        .build();

        ReceivedSettlementInvitationResponse secondInvitation =
                ReceivedSettlementInvitationResponse.builder()
                        .invitationId(1L)
                        .settlementId(1L)
                        .settlementTitle("제주도 여행비 정산")
                        .ownerName("김사이")
                        .invitationStatus(
                                SettlementInvitationStatus.INVITED
                        )
                        .invitedAt(
                                LocalDateTime.of(
                                        2026,
                                        8,
                                        4,
                                        15,
                                        13,
                                        46
                                )
                        )
                        .acceptedAt(null)
                        .build();

        List<ReceivedSettlementInvitationResponse> invitations =
                List.of(
                        firstInvitation,
                        secondInvitation
                );

        when(invitationMapper.findReceivedInvitations(userId))
                .thenReturn(invitations);

        List<ReceivedSettlementInvitationResponse> response =
                settlementInvitationService
                        .findReceivedInvitations(userId);

        assertThat(response)
                .hasSize(2)
                .containsExactly(
                        firstInvitation,
                        secondInvitation
                );

        assertThat(response.get(0).getInvitationId())
                .isEqualTo(3L);

        assertThat(response.get(0).getSettlementTitle())
                .isEqualTo("제주도");

        assertThat(response.get(0).getInvitationStatus())
                .isEqualTo(
                        SettlementInvitationStatus.INVITED
                );

        assertThat(response.get(0).getAcceptedAt())
                .isNull();

        assertThat(response.get(1).getInvitationId())
                .isEqualTo(1L);

        verify(invitationMapper)
                .findReceivedInvitations(userId);
    }

    @Test
    @DisplayName("받은 정산 초대가 없으면 빈 목록을 반환한다")
    void findReceivedInvitationsReturnsEmptyList() {
        Long userId = 2L;

        when(invitationMapper.findReceivedInvitations(userId))
                .thenReturn(List.of());

        List<ReceivedSettlementInvitationResponse> response =
                settlementInvitationService
                        .findReceivedInvitations(userId);

        assertThat(response)
                .isEmpty();

        verify(invitationMapper)
                .findReceivedInvitations(userId);
    }

    private CreateSettlementInvitationRequest
    createInvitationRequest(
            String userToken
    ) {
        CreateSettlementInvitationRequest request =
                mock(CreateSettlementInvitationRequest.class);

        when(request.getUserToken())
                .thenReturn(userToken);

        return request;
    }

    private UserDTO createUser(
            Long userId,
            String userToken,
            String name
    ) {
        UserDTO user = mock(UserDTO.class);

        when(user.getUserId())
                .thenReturn(userId);

        when(user.getUserToken())
                .thenReturn(userToken);

        when(user.getName())
                .thenReturn(name);

        return user;
    }
}
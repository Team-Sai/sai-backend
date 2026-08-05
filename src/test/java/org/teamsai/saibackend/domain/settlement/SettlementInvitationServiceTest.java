package org.teamsai.saibackend.domain.settlement;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.teamsai.saibackend.domain.settlement.dto.SettlementDTO;
import org.teamsai.saibackend.domain.settlement.dto.SettlementInvitationDTO;
import org.teamsai.saibackend.domain.settlement.dto.request.CreateSettlementInvitationRequest;
import org.teamsai.saibackend.domain.settlement.dto.response.CreateSettlementInvitationResponse;
import org.teamsai.saibackend.domain.settlement.dto.response.ReceivedSettlementInvitationResponse;
import org.teamsai.saibackend.domain.settlement.mapper.SettlementInvitationMapper;
import org.teamsai.saibackend.domain.settlement.mapper.SettlementMapper;
import org.teamsai.saibackend.domain.settlement.service.SettlementInvitationService;
import org.teamsai.saibackend.domain.settlement.type.SettlementInvitationStatus;
import org.teamsai.saibackend.domain.settlement.service.SettlementInvitationValidator;
import org.teamsai.saibackend.domain.user.dto.UserDTO;
import org.teamsai.saibackend.domain.user.service.UserService;
import org.teamsai.saibackend.global.exception.DomainException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SettlementInvitationServiceTest {

    private static final Long OWNER_ID = 1L;
    private static final Long SETTLEMENT_ID = 10L;
    private static final Long INVITED_USER_ID = 2L;
    private static final Long INVITATION_ID = 100L;
    private static final String USER_TOKEN = "target-user-token";

    @Mock
    private SettlementMapper settlementMapper;

    @Mock
    private SettlementInvitationMapper invitationMapper;

    @Mock
    private UserService userService;

    @Mock
    private SettlementInvitationValidator invitationValidator;

    @InjectMocks
    private SettlementInvitationService settlementInvitationService;

    @Test
    @DisplayName("정산 소유자는 회원토큰을 이용해 참여자를 초대할 수 있다")
    void inviteSuccess() {
        SettlementDTO settlement = mock(SettlementDTO.class);
        UserDTO invitedUser = mock(UserDTO.class);
        CreateSettlementInvitationRequest request =
                mock(CreateSettlementInvitationRequest.class);

        when(request.getUserToken()).thenReturn(USER_TOKEN);
        when(settlementMapper.findById(SETTLEMENT_ID))
                .thenReturn(Optional.of(settlement));

        when(userService.findRequestTarget(OWNER_ID, USER_TOKEN))
                .thenReturn(invitedUser);

        when(invitedUser.getUserId()).thenReturn(INVITED_USER_ID);
        when(invitedUser.getUserToken()).thenReturn(USER_TOKEN);
        when(invitedUser.getName()).thenReturn("초대회원");

        doAnswer(invocation -> {
            SettlementInvitationDTO invitation =
                    invocation.getArgument(0);

            ReflectionTestUtils.setField(
                    invitation,
                    "invitationId",
                    INVITATION_ID
            );

            return 1;
        }).when(invitationMapper)
                .insert(any(SettlementInvitationDTO.class));

        CreateSettlementInvitationResponse response =
                settlementInvitationService.invite(
                        OWNER_ID,
                        SETTLEMENT_ID,
                        request
                );

        assertThat(response.getInvitationId())
                .isEqualTo(INVITATION_ID);

        assertThat(response.getSettlementId())
                .isEqualTo(SETTLEMENT_ID);

        assertThat(response.getInvitedUserToken())
                .isEqualTo(USER_TOKEN);

        assertThat(response.getInvitedUserName())
                .isEqualTo("초대회원");

        assertThat(response.getInvitationStatus())
                .isEqualTo(SettlementInvitationStatus.INVITED);

        assertThat(response.getInvitedAt())
                .isNotNull();

        verify(invitationValidator)
                .validateInvitableSettlement(
                        settlement,
                        OWNER_ID
                );

        verify(invitationValidator)
                .validateInviteTarget(
                        SETTLEMENT_ID,
                        INVITED_USER_ID
                );

        ArgumentCaptor<SettlementInvitationDTO> captor =
                ArgumentCaptor.forClass(
                        SettlementInvitationDTO.class
                );

        verify(invitationMapper).insert(captor.capture());

        SettlementInvitationDTO savedInvitation =
                captor.getValue();

        assertThat(savedInvitation.getSettlementId())
                .isEqualTo(SETTLEMENT_ID);

        assertThat(savedInvitation.getInvitedUserId())
                .isEqualTo(INVITED_USER_ID);

        assertThat(savedInvitation.getInvitationStatus())
                .isEqualTo(SettlementInvitationStatus.INVITED);

        assertThat(savedInvitation.getInvitedAt())
                .isNotNull();

        assertThat(savedInvitation.getAcceptedAt())
                .isNull();
    }

    @Test
    @DisplayName("존재하지 않는 정산에는 초대를 생성할 수 없다")
    void inviteFailWhenSettlementNotFound() {
        CreateSettlementInvitationRequest request =
                mock(CreateSettlementInvitationRequest.class);

        when(settlementMapper.findById(SETTLEMENT_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(
                () -> settlementInvitationService.invite(
                        OWNER_ID,
                        SETTLEMENT_ID,
                        request
                )
        ).isInstanceOf(DomainException.class);

        verifyNoInteractions(
                invitationValidator,
                userService
        );

        verify(
                invitationMapper,
                never()
        ).insert(any());
    }

    @Test
    @DisplayName("초대 저장 결과가 1건이 아니면 예외가 발생한다")
    void inviteFailWhenInsertFailed() {
        SettlementDTO settlement = mock(SettlementDTO.class);
        UserDTO invitedUser = mock(UserDTO.class);
        CreateSettlementInvitationRequest request =
                mock(CreateSettlementInvitationRequest.class);

        when(request.getUserToken()).thenReturn(USER_TOKEN);

        when(settlementMapper.findById(SETTLEMENT_ID))
                .thenReturn(Optional.of(settlement));

        when(userService.findRequestTarget(OWNER_ID, USER_TOKEN))
                .thenReturn(invitedUser);

        when(invitedUser.getUserId())
                .thenReturn(INVITED_USER_ID);

        when(invitationMapper.insert(any()))
                .thenReturn(0);

        assertThatThrownBy(
                () -> settlementInvitationService.invite(
                        OWNER_ID,
                        SETTLEMENT_ID,
                        request
                )
        ).isInstanceOf(DomainException.class);

        verify(invitationMapper)
                .insert(any(SettlementInvitationDTO.class));
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
                        .invitationStatus(SettlementInvitationStatus.INVITED)
                        .invitedAt(
                                LocalDateTime.of(2026, 8, 4, 16, 0, 31
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
                        .invitationStatus(SettlementInvitationStatus.INVITED)
                        .invitedAt(
                                LocalDateTime.of(2026, 8, 4, 15, 13, 46
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
                .isEqualTo(SettlementInvitationStatus.INVITED);

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
}
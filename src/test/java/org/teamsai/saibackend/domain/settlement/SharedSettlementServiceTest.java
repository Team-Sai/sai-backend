package org.teamsai.saibackend.domain.settlement;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.teamsai.saibackend.domain.settlement.dto.SettlementDTO;
import org.teamsai.saibackend.domain.settlement.dto.request.CreateSharedSettlementRequest;
import org.teamsai.saibackend.domain.settlement.dto.response.CreateSharedSettlementResponse;
import org.teamsai.saibackend.domain.settlement.dto.response.SettlementDetailResponse;
import org.teamsai.saibackend.domain.settlement.dto.response.SettlementListResponse;
import org.teamsai.saibackend.domain.settlement.exception.SettlementErrorCode;
import org.teamsai.saibackend.domain.settlement.mapper.SettlementMapper;
import org.teamsai.saibackend.domain.settlement.service.SettlementAccountService;
import org.teamsai.saibackend.domain.settlement.service.SharedSettlementService;
import org.teamsai.saibackend.domain.settlement.type.SettlementStatus;
import org.teamsai.saibackend.domain.settlement.type.SettlementType;
import org.teamsai.saibackend.domain.settlement.type.SplitType;
import org.teamsai.saibackend.global.exception.DomainException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("SharedSettlementService 단위 테스트")
class SharedSettlementServiceTest {

    private static final Long OWNER_ID = 1L;
    private static final Long SETTLEMENT_ID = 15L;

    private static final Long LINKED_ACCOUNT_ID = 10L;

    @Mock
    private SettlementMapper settlementMapper;

    @Mock
    private SettlementInvitationService invitationService;

    @Mock
    private SettlementAccountService settlementAccountService;

    @InjectMocks
    private SharedSettlementService sharedSettlementService;

    @Test
    @DisplayName(
            "공동정산 생성 시 총금액을 생성자 포함 전체 인원으로 균등 분배한다"
    )
    void createSharedSettlementSuccess() {
        CreateSharedSettlementRequest request =
                createRequest(
                        new BigDecimal("450000"),
                        List.of(
                                invitation("SAI_USER_A"),
                                invitation("SAI_USER_B")
                        )
                );

        when(settlementMapper.insertSettlement(
                any(SettlementDTO.class)
        )).thenAnswer(invocation -> {
            SettlementDTO settlement =
                    invocation.getArgument(0);

            settlement.setSettlementId(SETTLEMENT_ID);

            return 1;
        });

        CreateSharedSettlementResponse response =
                sharedSettlementService.create(
                        OWNER_ID,
                        request
                );

        ArgumentCaptor<SettlementDTO> settlementCaptor =
                ArgumentCaptor.forClass(SettlementDTO.class);

        verify(settlementMapper)
                .insertSettlement(settlementCaptor.capture());

        SettlementDTO savedSettlement =
                settlementCaptor.getValue();

        assertThat(savedSettlement.getOwnerId())
                .isEqualTo(OWNER_ID);

        assertThat(savedSettlement.getSettlementType())
                .isEqualTo(SettlementType.SHARED);

        assertThat(savedSettlement.getSettlementStatus())
                .isEqualTo(SettlementStatus.IN_PROGRESS);

        assertThat(savedSettlement.getSettlementCategory())
                .isEqualTo("여행");

        assertThat(savedSettlement.getTitle())
                .isEqualTo("제주도 여행비 정산");

        assertThat(savedSettlement.getSplitType())
                .isEqualTo(SplitType.EQUAL);

        assertThat(savedSettlement.getTotalAmount())
                .isEqualByComparingTo("450000");

        assertThat(savedSettlement.getDueDate())
                .isEqualTo(request.getDueDate());

        assertThat(savedSettlement.getCreatedAt())
                .isNotNull();

        // 공동정산에서는 정기정산 관련 값을 저장하지 않는다.
        assertThat(savedSettlement.getCycleRule()).isNull();
        assertThat(savedSettlement.getStartDate()).isNull();
        assertThat(savedSettlement.getEndDate()).isNull();
        assertThat(savedSettlement.getCycleDate()).isNull();
        assertThat(savedSettlement.getClosedAt()).isNull();

        /*
         * 초대 대상자 2명 + 생성자 1명 = 총 3명
         * 450,000 / 3 = 150,000
         */
        ArgumentCaptor<BigDecimal> expectedAmountCaptor =
                ArgumentCaptor.forClass(BigDecimal.class);

        verify(invitationService)
                .inviteAll(
                        eq(OWNER_ID),
                        eq(SETTLEMENT_ID),
                        same(request.getInvitations()),
                        expectedAmountCaptor.capture()
                );
        verify(settlementAccountService)
            .selectAccount(
                OWNER_ID,
                SETTLEMENT_ID,
                LINKED_ACCOUNT_ID
            );

        assertThat(expectedAmountCaptor.getValue())
                .isEqualByComparingTo("150000");

        assertThat(response.getSettlementId())
                .isEqualTo(SETTLEMENT_ID);

        assertThat(response.getSettlementType())
                .isEqualTo(SettlementType.SHARED);

        assertThat(response.getSettlementStatus())
                .isEqualTo(SettlementStatus.IN_PROGRESS);

        assertThat(response.getTitle())
                .isEqualTo("제주도 여행비 정산");

        assertThat(response.getCreatedAt())
                .isNotNull();
    }

    @Test
    @DisplayName(
            "총금액이 인원수로 나누어떨어지지 않으면 원 단위로 내림한다"
    )
    void createSharedSettlementRoundsAmountDown() {
        CreateSharedSettlementRequest request =
                createRequest(
                        new BigDecimal("10000"),
                        List.of(
                                invitation("SAI_USER_A"),
                                invitation("SAI_USER_B")
                        )
                );

        when(settlementMapper.insertSettlement(
                any(SettlementDTO.class)
        )).thenAnswer(invocation -> {
            SettlementDTO settlement =
                    invocation.getArgument(0);

            settlement.setSettlementId(SETTLEMENT_ID);

            return 1;
        });

        sharedSettlementService.create(
                OWNER_ID,
                request
        );

        ArgumentCaptor<BigDecimal> expectedAmountCaptor =
                ArgumentCaptor.forClass(BigDecimal.class);

        verify(invitationService)
                .inviteAll(
                        eq(OWNER_ID),
                        eq(SETTLEMENT_ID),
                        same(request.getInvitations()),
                        expectedAmountCaptor.capture()
                );

        /*
         * 10,000 / 3 = 3,333.333...
         * 원 단위 내림 결과는 3,333원
         */
        assertThat(expectedAmountCaptor.getValue())
                .isEqualByComparingTo("3333");
    }

    @Test
    @DisplayName(
            "동일한 회원 코드를 중복 선택하면 정산을 생성하지 않는다"
    )
    void createSharedSettlementFailsWhenInviteeIsDuplicated() {
        CreateSharedSettlementRequest request =
                createRequest(
                        new BigDecimal("30000"),
                        List.of(
                                invitation("SAI_DUPLICATE"),
                                invitation("SAI_DUPLICATE")
                        )
                );

        assertSettlementExceptionThrownBy(
                () -> sharedSettlementService.create(
                        OWNER_ID,
                        request
                ),
                SettlementErrorCode.DUPLICATE_SETTLEMENT_INVITEE
        );

        verifyNoInteractions(
                settlementMapper,
                invitationService
        );
    }

    @Test
    @DisplayName(
            "총금액이 0 이하이면 공동정산을 생성하지 않는다"
    )
    void createSharedSettlementFailsWhenTotalAmountIsInvalid() {
        CreateSharedSettlementRequest request =
                createRequest(
                        BigDecimal.ZERO,
                        List.of(
                                invitation("SAI_USER_A")
                        )
                );

        assertSettlementExceptionThrownBy(
                () -> sharedSettlementService.create(
                        OWNER_ID,
                        request
                ),
                SettlementErrorCode.INVALID_SETTLEMENT_AMOUNT
        );

        verifyNoInteractions(
                settlementMapper,
                invitationService
        );
    }

    @Test
    @DisplayName(
            "공동정산 저장 결과가 1건이 아니면 초대를 생성하지 않는다"
    )
    void createSharedSettlementFailsWhenInsertCountIsInvalid() {
        CreateSharedSettlementRequest request =
                createRequest(
                        new BigDecimal("30000"),
                        List.of(
                                invitation("SAI_USER_A")
                        )
                );

        when(settlementMapper.insertSettlement(
                any(SettlementDTO.class)
        )).thenReturn(0);

        assertSettlementExceptionThrownBy(
                () -> sharedSettlementService.create(
                        OWNER_ID,
                        request
                ),
                SettlementErrorCode.SETTLEMENT_CREATE_FAILED
        );

        verify(invitationService, never())
                .inviteAll(
                        any(),
                        any(),
                        any(),
                        any()
                );
    }

    private CreateSharedSettlementRequest createRequest(
            BigDecimal totalAmount,
            List<CreateSettlementInvitationRequest> invitations
    ) {
        return CreateSharedSettlementRequest.builder()
                .settlementCategory("여행")
                .title("제주도 여행비 정산")
                .dueDate(LocalDate.now().plusDays(7))
                .totalAmount(totalAmount)
                .linkedAccountId(LINKED_ACCOUNT_ID)
                .invitations(invitations)
                .build();
    }

    @Test
    @DisplayName("수취 계좌 설정에 실패하면 정산 생성에 실패한다")
    void failsWhenSettlementAccountSelectionFails() {

        CreateSharedSettlementRequest request =
            createRequest(
                new BigDecimal("30000"),
                List.of(
                    invitation("SAI_USER_A")
                )
            );

        given(settlementMapper
            .insertSettlement(any(SettlementDTO.class)))
            .willAnswer(invocation -> {
                SettlementDTO settlement =
                    invocation.getArgument(0);

                settlement.setSettlementId(
                    SETTLEMENT_ID
                );

                return 1;
            });

        given(
            settlementAccountService.selectAccount(
                OWNER_ID,
                SETTLEMENT_ID,
                LINKED_ACCOUNT_ID
            )
        ).willThrow(
            SettlementErrorCode
                .SETTLEMENT_ACCOUNT_CREATE_FAILED
                .toException()
        );

        assertThatThrownBy(() ->
            sharedSettlementService.create(
                OWNER_ID,
                request
            )
        ).isInstanceOf(DomainException.class);

        then(invitationService)
            .should()
            .inviteAll(
                OWNER_ID,
                SETTLEMENT_ID,
                request.getInvitations(),
                new BigDecimal("15000")
            );

        then(settlementAccountService)
            .should()
            .selectAccount(
                OWNER_ID,
                SETTLEMENT_ID,
                LINKED_ACCOUNT_ID
            );
    }

    @Test
    @DisplayName("사용자의 정산 목록을 조회한다")
    void getSettlementListSuccess() {
        Long userId = 1L;

        List<SettlementListResponse> expected =
                List.of(
                        mock(SettlementListResponse.class),
                        mock(SettlementListResponse.class)
                );

        when(settlementMapper.findAllByUserId(userId))
                .thenReturn(expected);

        List<SettlementListResponse> result =
                sharedSettlementService.getSettlementList(userId);

        assertThat(result)
                .isEqualTo(expected);

        verify(settlementMapper)
                .findAllByUserId(userId);
    }

    @Test
    @DisplayName("조회되는 정산이 없으면 빈 목록을 반환한다")
    void getSettlementListReturnsEmptyList() {
        Long userId = 1L;

        when(settlementMapper.findAllByUserId(userId))
                .thenReturn(List.of());

        List<SettlementListResponse> result =
                sharedSettlementService.getSettlementList(userId);

        assertThat(result)
                .isEmpty();

        verify(settlementMapper)
                .findAllByUserId(userId);
    }

    @Test
    @DisplayName("정산 생성자는 정산 상세를 조회할 수 있다")
    void getSettlementDetailByOwner() {
        SettlementDetailResponse detail = createDetail("OWNER");

        given(settlementMapper.findDetailById(
                SETTLEMENT_ID,
                OWNER_ID
        )).willReturn(Optional.of(detail));

        SettlementDetailResponse result =
                sharedSettlementService.getSettlementDetail(
                        SETTLEMENT_ID,
                        OWNER_ID
                );

        assertThat(result.settlementId()).isEqualTo(SETTLEMENT_ID);
        assertThat(result.title()).isEqualTo("테스트 정산");
        assertThat(result.role()).isEqualTo("OWNER");
    }

    @Test
    @DisplayName("정산 참여자는 정산 상세를 조회할 수 있다")
    void getSettlementDetailByMember() {
        Long memberId = 2L;

        given(settlementMapper.findDetailById(
                SETTLEMENT_ID,
                memberId
        )).willReturn(Optional.of(createDetail("MEMBER")));

        SettlementDetailResponse result =
                sharedSettlementService.getSettlementDetail(
                        SETTLEMENT_ID,
                        memberId
                );

        assertThat(result.role()).isEqualTo("MEMBER");
    }

    @Test
    @DisplayName("정산과 관계없는 사용자는 상세를 조회할 수 없다")
    void getSettlementDetailFailsWhenNotParticipant() {
        Long otherUserId = 3L;

        given(settlementMapper.findDetailById(
                SETTLEMENT_ID,
                otherUserId
        )).willReturn(Optional.of(createDetail("NONE")));

        assertThatThrownBy(() ->
                sharedSettlementService.getSettlementDetail(
                        SETTLEMENT_ID,
                        otherUserId
                )
        ).isInstanceOf(DomainException.class);
    }

    private CreateSettlementInvitationRequest invitation(
            String userToken
    ) {
        return CreateSettlementInvitationRequest.builder()
                .userToken(userToken)
                .build();
    }

    private void assertSettlementExceptionThrownBy(
            Runnable operation,
            SettlementErrorCode errorCode
    ) {
        assertThatThrownBy(operation::run)
                .isInstanceOfSatisfying(
                        DomainException.class,
                        exception -> assertThat(
                                exception.getErrorCode()
                        ).isEqualTo(errorCode)
                );
    }

    private SettlementDetailResponse createDetail(String role) {
        return new SettlementDetailResponse(
                SETTLEMENT_ID,
                "테스트 정산",
                "모임",
                "SHARED",
                "IN_PROGRESS",
                "EQUAL",
                LocalDate.now().plusDays(7),
                LocalDateTime.now(),
                role
        );
    }
}
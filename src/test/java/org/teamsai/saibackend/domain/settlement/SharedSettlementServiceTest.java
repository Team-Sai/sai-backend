package org.teamsai.saibackend.domain.settlement;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.teamsai.saibackend.domain.settlement.dto.SettlementDTO;
import org.teamsai.saibackend.domain.settlement.dto.request.CreateSettlementParticipantRequest;
import org.teamsai.saibackend.domain.settlement.dto.request.CreateSharedSettlementRequest;
import org.teamsai.saibackend.domain.settlement.dto.response.CreateSharedSettlementResponse;
import org.teamsai.saibackend.domain.settlement.exception.SettlementErrorCode;
import org.teamsai.saibackend.domain.settlement.mapper.SettlementMapper;
import org.teamsai.saibackend.domain.settlement.service.SettlementAccountService;
import org.teamsai.saibackend.domain.settlement.service.SettlementAmountCalculator;
import org.teamsai.saibackend.domain.settlement.service.SettlementParticipantRegistrationService;
import org.teamsai.saibackend.domain.settlement.service.SettlementValidator;
import org.teamsai.saibackend.domain.settlement.service.SharedSettlementService;
import org.teamsai.saibackend.domain.settlement.type.SettlementStatus;
import org.teamsai.saibackend.domain.settlement.type.SettlementType;
import org.teamsai.saibackend.domain.settlement.type.SplitType;
import org.teamsai.saibackend.global.exception.DomainException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
@DisplayName("SharedSettlementService 단위 테스트")
class SharedSettlementServiceTest {

    private static final Long OWNER_ID = 1L;
    private static final Long SETTLEMENT_ID = 15L;
    private static final Long LINKED_ACCOUNT_ID = 10L;


    @Mock
    private SettlementMapper settlementMapper;

    @Mock
    private SettlementAccountService settlementAccountService;

    @Mock
    private SettlementValidator settlementValidator;

    @Mock
    private SettlementParticipantRegistrationService
            participantRegistrationService;

    @Mock
    private SettlementAmountCalculator settlementAmountCalculator;

    @InjectMocks
    private SharedSettlementService sharedSettlementService;


    @Test
    @DisplayName(
            "공동정산 생성 시 정산을 저장하고 참여자 등록과 수취 계좌 설정을 요청한다"
    )
    void createSharedSettlementSuccess() {

        CreateSharedSettlementRequest request =
                createRequest(
                        new BigDecimal("450000"),
                        List.of(
                                participant("SAI_USER_A"),
                                participant("SAI_USER_B")
                        )
                );

        BigDecimal expectedAmount =
                new BigDecimal("150000");


        given(
                settlementAmountCalculator.calculateEqualAmount(
                        request.getTotalAmount(),
                        request.getParticipants().size()
                )
        ).willReturn(
                expectedAmount
        );


        given(
                settlementMapper.insertSettlement(
                        any(SettlementDTO.class)
                )
        ).willAnswer(invocation -> {

            SettlementDTO settlement =
                    invocation.getArgument(0);

            settlement.setSettlementId(
                    SETTLEMENT_ID
            );

            return 1;
        });


        CreateSharedSettlementResponse response =
                sharedSettlementService.create(
                        OWNER_ID,
                        request
                );


        ArgumentCaptor<SettlementDTO> settlementCaptor =
                ArgumentCaptor.forClass(
                        SettlementDTO.class
                );

        verify(settlementMapper)
                .insertSettlement(
                        settlementCaptor.capture()
                );


        SettlementDTO savedSettlement =
                settlementCaptor.getValue();


        assertThat(savedSettlement.getOwnerId())
                .isEqualTo(OWNER_ID);

        assertThat(savedSettlement.getSettlementType())
                .isEqualTo(
                        SettlementType.SHARED
                );

        assertThat(savedSettlement.getSettlementStatus())
                .isEqualTo(
                        SettlementStatus.IN_PROGRESS
                );

        assertThat(savedSettlement.getSettlementCategory())
                .isEqualTo("여행");

        assertThat(savedSettlement.getTitle())
                .isEqualTo(
                        "제주도 여행비 정산"
                );

        assertThat(savedSettlement.getSplitType())
                .isEqualTo(
                        SplitType.EQUAL
                );

        assertThat(savedSettlement.getTotalAmount())
                .isEqualByComparingTo(
                        "450000"
                );

        assertThat(savedSettlement.getRecurringSettlementId())
                .isNull();

        assertThat(savedSettlement.getCycleDate())
                .isNull();


        verify(settlementValidator)
                .validateCreateRequest(
                        request
                );


        verify(settlementAmountCalculator)
                .calculateEqualAmount(
                        request.getTotalAmount(),
                        request.getParticipants().size()
                );


        verify(participantRegistrationService)
                .registerParticipants(
                        OWNER_ID,
                        SETTLEMENT_ID,
                        request.getParticipants(),
                        expectedAmount
                );


        verify(settlementAccountService)
                .selectAccount(
                        OWNER_ID,
                        SETTLEMENT_ID,
                        LINKED_ACCOUNT_ID
                );


        assertThat(response.getSettlementId())
                .isEqualTo(SETTLEMENT_ID);

        assertThat(response.getSettlementType())
                .isEqualTo(
                        SettlementType.SHARED
                );

        assertThat(response.getSettlementStatus())
                .isEqualTo(
                        SettlementStatus.IN_PROGRESS
                );

        assertThat(response.getTitle())
                .isEqualTo(
                        "제주도 여행비 정산"
                );
    }


    @Test
    @DisplayName(
            "공동정산 저장에 실패하면 참여자 등록과 수취 계좌 설정을 하지 않는다"
    )
    void createSharedSettlementFailsWhenInsertCountIsInvalid() {

        CreateSharedSettlementRequest request =
                createRequest(
                        new BigDecimal("30000"),
                        List.of(
                                participant(
                                        "SAI_USER_A"
                                )
                        )
                );

        BigDecimal expectedAmount =
                new BigDecimal("15000");


        given(
                settlementAmountCalculator.calculateEqualAmount(
                        request.getTotalAmount(),
                        request.getParticipants().size()
                )
        ).willReturn(
                expectedAmount
        );


        given(
                settlementMapper.insertSettlement(
                        any(SettlementDTO.class)
                )
        ).willReturn(0);


        assertSettlementExceptionThrownBy(
                () ->
                        sharedSettlementService.create(
                                OWNER_ID,
                                request
                        ),
                SettlementErrorCode
                        .SETTLEMENT_CREATE_FAILED
        );


        verify(settlementValidator)
                .validateCreateRequest(
                        request
                );

        verify(settlementAmountCalculator)
                .calculateEqualAmount(
                        request.getTotalAmount(),
                        request.getParticipants().size()
                );

        verifyNoInteractions(
                participantRegistrationService,
                settlementAccountService
        );
    }


    @Test
    @DisplayName(
            "수취 계좌 설정에 실패하면 정산 생성에 실패한다"
    )
    void failsWhenSettlementAccountSelectionFails() {

        CreateSharedSettlementRequest request =
                createRequest(
                        new BigDecimal("30000"),
                        List.of(
                                participant(
                                        "SAI_USER_A"
                                )
                        )
                );

        BigDecimal expectedAmount =
                new BigDecimal("15000");


        given(
                settlementAmountCalculator.calculateEqualAmount(
                        request.getTotalAmount(),
                        request.getParticipants().size()
                )
        ).willReturn(
                expectedAmount
        );


        given(
                settlementMapper.insertSettlement(
                        any(SettlementDTO.class)
                )
        ).willAnswer(invocation -> {

            SettlementDTO settlement =
                    invocation.getArgument(0);

            settlement.setSettlementId(
                    SETTLEMENT_ID
            );

            return 1;
        });


        given(
                settlementAccountService
                        .selectAccount(
                                OWNER_ID,
                                SETTLEMENT_ID,
                                LINKED_ACCOUNT_ID
                        )
        ).willThrow(
                SettlementErrorCode
                        .SETTLEMENT_ACCOUNT_CREATE_FAILED
                        .toException()
        );


        assertThatThrownBy(
                () ->
                        sharedSettlementService.create(
                                OWNER_ID,
                                request
                        )
        ).isInstanceOf(
                DomainException.class
        );


        verify(participantRegistrationService)
                .registerParticipants(
                        OWNER_ID,
                        SETTLEMENT_ID,
                        request.getParticipants(),
                        expectedAmount
                );


        verify(settlementAccountService)
                .selectAccount(
                        OWNER_ID,
                        SETTLEMENT_ID,
                        LINKED_ACCOUNT_ID
                );
    }


    private CreateSharedSettlementRequest createRequest(
            BigDecimal totalAmount,
            List<CreateSettlementParticipantRequest> participants
    ) {

        return CreateSharedSettlementRequest
                .builder()
                .settlementCategory(
                        "여행"
                )
                .title(
                        "제주도 여행비 정산"
                )
                .dueDate(
                        LocalDate.now()
                                .plusDays(7)
                )
                .totalAmount(
                        totalAmount
                )
                .linkedAccountId(
                        LINKED_ACCOUNT_ID
                )
                .participants(
                        participants
                )
                .build();
    }


    private CreateSettlementParticipantRequest participant(
            String userToken
    ) {

        return CreateSettlementParticipantRequest
                .builder()
                .userToken(
                        userToken
                )
                .build();
    }


    private void assertSettlementExceptionThrownBy(
            Runnable operation,
            SettlementErrorCode errorCode
    ) {

        assertThatThrownBy(
                operation::run
        ).isInstanceOfSatisfying(
                DomainException.class,
                exception ->
                        assertThat(
                                exception.getErrorCode()
                        ).isEqualTo(
                                errorCode
                        )
        );
    }
}
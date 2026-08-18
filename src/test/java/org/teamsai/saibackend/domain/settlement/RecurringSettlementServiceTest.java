package org.teamsai.saibackend.domain.settlement;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.teamsai.saibackend.domain.settlement.dto.RecurringSettlementDTO;
import org.teamsai.saibackend.domain.settlement.dto.SettlementDTO;
import org.teamsai.saibackend.domain.settlement.dto.request.CreateRecurringSettlementRequest;
import org.teamsai.saibackend.domain.settlement.dto.request.CreateSettlementParticipantRequest;
import org.teamsai.saibackend.domain.settlement.dto.response.CreateRecurringSettlementResponse;
import org.teamsai.saibackend.domain.settlement.exception.SettlementErrorCode;
import org.teamsai.saibackend.domain.settlement.mapper.RecurringSettlementManagementMapper;
import org.teamsai.saibackend.domain.settlement.mapper.SettlementMapper;
import org.teamsai.saibackend.domain.settlement.service.*;
import org.teamsai.saibackend.domain.settlement.type.CycleRule;
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
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
@DisplayName("RecurringSettlementService 단위 테스트")
class RecurringSettlementServiceTest {

    private static final Long OWNER_ID = 1L;
    private static final Long RECURRING_SETTLEMENT_ID = 10L;
    private static final Long FIRST_SETTLEMENT_ID = 100L;
    private static final Long LINKED_ACCOUNT_ID = 5L;

    @Mock
    private RecurringSettlementManagementMapper recurringSettlementManagementMapper;

    @Mock
    private SettlementMapper settlementMapper;

    @Mock
    private RecurringSettlementValidator recurringSettlementValidator;

    @Mock
    private SettlementAmountCalculator settlementAmountCalculator;

    @Mock
    private SettlementParticipantRegistrationService participantRegistrationService;

    @Mock
    private SettlementAccountService settlementAccountService;

    @InjectMocks
    private RecurringSettlementService recurringSettlementService;

    @Test
    @DisplayName("정기정산 생성 시 정기 설정과 최초 회차를 생성한다")
    void createRecurringSettlementSuccess() {
        CreateRecurringSettlementRequest request = createRequest();

        BigDecimal perPersonAmount = new BigDecimal("150000");

        given(settlementAmountCalculator.calculateEqualAmount(
                request.getTotalAmount(),
                request.getParticipants().size()
        )).willReturn(perPersonAmount);

        given(recurringSettlementManagementMapper.insert(
                any(RecurringSettlementDTO.class)
        )).willAnswer(invocation -> {
            RecurringSettlementDTO recurring = invocation.getArgument(0);

            ReflectionTestUtils.setField(
                    recurring,
                    "recurringSettlementId",
                    RECURRING_SETTLEMENT_ID
            );

            return 1;
        });

        given(settlementMapper.insertSettlement(
                any(SettlementDTO.class)
        )).willAnswer(invocation -> {
            SettlementDTO settlement = invocation.getArgument(0);
            settlement.setSettlementId(FIRST_SETTLEMENT_ID);
            return 1;
        });

        CreateRecurringSettlementResponse response =
                recurringSettlementService.create(
                        OWNER_ID,
                        request
                );

        ArgumentCaptor<RecurringSettlementDTO> recurringCaptor =
                ArgumentCaptor.forClass(RecurringSettlementDTO.class);

        ArgumentCaptor<SettlementDTO> settlementCaptor =
                ArgumentCaptor.forClass(SettlementDTO.class);

        then(recurringSettlementValidator)
                .should()
                .validateCreateRequest(request);

        then(settlementAmountCalculator)
                .should()
                .calculateEqualAmount(
                        request.getTotalAmount(),
                        request.getParticipants().size()
                );

        then(recurringSettlementManagementMapper)
                .should()
                .insert(recurringCaptor.capture());

        RecurringSettlementDTO savedRecurring =
                recurringCaptor.getValue();

        assertThat(savedRecurring.getOwnerId())
                .isEqualTo(OWNER_ID);

        assertThat(savedRecurring.getSettlementCategory())
                .isEqualTo("OTT·구독");

        assertThat(savedRecurring.getTitle())
                .isEqualTo("넷플릭스 구독");

        assertThat(savedRecurring.getSplitType())
                .isEqualTo(SplitType.EQUAL);

        assertThat(savedRecurring.getTotalAmount())
                .isEqualByComparingTo("450000");

        assertThat(savedRecurring.getCycleRule())
                .isEqualTo(CycleRule.MONTHLY);

        assertThat(savedRecurring.getStartDate())
                .isEqualTo(LocalDate.of(2026, 8, 17));

        assertThat(savedRecurring.getEndDate())
                .isEqualTo(LocalDate.of(2027, 8, 17));

        then(settlementMapper)
                .should()
                .insertSettlement(
                        settlementCaptor.capture()
                );

        SettlementDTO firstSettlement =
                settlementCaptor.getValue();

        assertThat(firstSettlement.getRecurringSettlementId())
                .isEqualTo(RECURRING_SETTLEMENT_ID);

        assertThat(firstSettlement.getOwnerId())
                .isEqualTo(OWNER_ID);

        assertThat(firstSettlement.getSettlementType())
                .isEqualTo(SettlementType.RECURRING);

        assertThat(firstSettlement.getSettlementStatus())
                .isEqualTo(SettlementStatus.IN_PROGRESS);

        assertThat(firstSettlement.getSettlementCategory())
                .isEqualTo("OTT·구독");

        assertThat(firstSettlement.getTitle())
                .isEqualTo("넷플릭스 구독");

        assertThat(firstSettlement.getSplitType())
                .isEqualTo(SplitType.EQUAL);

        assertThat(firstSettlement.getTotalAmount())
                .isEqualByComparingTo("450000");

        assertThat(firstSettlement.getCycleDate())
                .isEqualTo(LocalDate.of(2026, 8, 17));

        assertThat(firstSettlement.getDueDate())
                .isNull();

        then(participantRegistrationService)
                .should()
                .registerParticipants(
                        OWNER_ID,
                        FIRST_SETTLEMENT_ID,
                        request.getParticipants(),
                        perPersonAmount
                );

        then(settlementAccountService)
                .should()
                .selectAccount(
                        OWNER_ID,
                        FIRST_SETTLEMENT_ID,
                        LINKED_ACCOUNT_ID
                );

        assertThat(response.getRecurringSettlementId())
                .isEqualTo(RECURRING_SETTLEMENT_ID);

        assertThat(response.getFirstSettlementId())
                .isEqualTo(FIRST_SETTLEMENT_ID);

        assertThat(response.getSettlementType())
                .isEqualTo(SettlementType.RECURRING);

        assertThat(response.getCycleRule())
                .isEqualTo(CycleRule.MONTHLY);

        assertThat(response.getStartDate())
                .isEqualTo(LocalDate.of(2026, 8, 17));

        assertThat(response.getEndDate())
                .isEqualTo(LocalDate.of(2027, 8, 17));
    }

    @Test
    @DisplayName("정기정산 설정 저장에 실패하면 최초 회차를 생성하지 않는다")
    void createFailsWhenRecurringSettlementInsertFails() {
        CreateRecurringSettlementRequest request =
                createRequest();

        given(settlementAmountCalculator.calculateEqualAmount(
                request.getTotalAmount(),
                request.getParticipants().size()
        )).willReturn(
                new BigDecimal("150000")
        );

        given(recurringSettlementManagementMapper.insert(
                any(RecurringSettlementDTO.class)
        )).willReturn(0);

        assertThatThrownBy(() ->
                recurringSettlementService.create(
                        OWNER_ID,
                        request
                )
        ).isInstanceOfSatisfying(
                DomainException.class,
                exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(
                                        SettlementErrorCode.SETTLEMENT_CREATE_FAILED
                                )
        );

        verifyNoInteractions(
                settlementMapper,
                participantRegistrationService,
                settlementAccountService
        );
    }

    @Test
    @DisplayName("최초 회차 생성에 실패하면 참여자와 수취 계좌를 생성하지 않는다")
    void createFailsWhenFirstSettlementInsertFails() {
        CreateRecurringSettlementRequest request =
                createRequest();

        given(settlementAmountCalculator.calculateEqualAmount(
                request.getTotalAmount(),
                request.getParticipants().size()
        )).willReturn(
                new BigDecimal("150000")
        );

        given(recurringSettlementManagementMapper.insert(
                any(RecurringSettlementDTO.class)
        )).willAnswer(invocation -> {
            RecurringSettlementDTO recurring =
                    invocation.getArgument(0);

            ReflectionTestUtils.setField(
                    recurring,
                    "recurringSettlementId",
                    RECURRING_SETTLEMENT_ID
            );

            return 1;
        });

        given(settlementMapper.insertSettlement(
                any(SettlementDTO.class)
        )).willReturn(0);

        assertThatThrownBy(() ->
                recurringSettlementService.create(
                        OWNER_ID,
                        request
                )
        ).isInstanceOfSatisfying(
                DomainException.class,
                exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(
                                        SettlementErrorCode.SETTLEMENT_CREATE_FAILED
                                )
        );

        verifyNoInteractions(
                participantRegistrationService,
                settlementAccountService
        );
    }

    @Test
    @DisplayName("정기정산 요청 검증에 실패하면 어떤 데이터도 생성하지 않는다")
    void createFailsWhenValidationFails() {
        CreateRecurringSettlementRequest request =
                createRequest();

        willThrow(
                SettlementErrorCode
                        .DUPLICATE_SETTLEMENT_PARTICIPANT
                        .toException()
        )
                .given(recurringSettlementValidator)
                .validateCreateRequest(request);

        assertThatThrownBy(() ->
                recurringSettlementService.create(
                        OWNER_ID,
                        request
                )
        ).isInstanceOf(DomainException.class);

        verifyNoInteractions(
                settlementAmountCalculator,
                recurringSettlementManagementMapper,
                settlementMapper,
                participantRegistrationService,
                settlementAccountService
        );
    }

    @Test
    @DisplayName("참여자 등록 시 공동정산과 동일한 균등 분배 금액을 전달한다")
    void usesSharedSettlementAmountCalculationPolicy() {
        CreateRecurringSettlementRequest request =
                createRequest();

        BigDecimal expectedAmount =
                new BigDecimal("150000");

        given(settlementAmountCalculator.calculateEqualAmount(
                new BigDecimal("450000"),
                2
        )).willReturn(expectedAmount);

        given(recurringSettlementManagementMapper.insert(
                any(RecurringSettlementDTO.class)
        )).willAnswer(invocation -> {
            RecurringSettlementDTO recurring =
                    invocation.getArgument(0);

            ReflectionTestUtils.setField(
                    recurring,
                    "recurringSettlementId",
                    RECURRING_SETTLEMENT_ID
            );

            return 1;
        });

        given(settlementMapper.insertSettlement(
                any(SettlementDTO.class)
        )).willAnswer(invocation -> {
            SettlementDTO settlement =
                    invocation.getArgument(0);

            settlement.setSettlementId(
                    FIRST_SETTLEMENT_ID
            );

            return 1;
        });

        recurringSettlementService.create(
                OWNER_ID,
                request
        );

        then(settlementAmountCalculator)
                .should()
                .calculateEqualAmount(
                        new BigDecimal("450000"),
                        2
                );

        then(participantRegistrationService)
                .should()
                .registerParticipants(
                        OWNER_ID,
                        FIRST_SETTLEMENT_ID,
                        request.getParticipants(),
                        expectedAmount
                );
    }

    @Test
    @DisplayName("수취 계좌 설정 실패 시 예외를 그대로 전달한다")
    void createFailsWhenSettlementAccountCreationFails() {
        CreateRecurringSettlementRequest request =
                createRequest();

        BigDecimal perPersonAmount =
                new BigDecimal("150000");

        given(settlementAmountCalculator.calculateEqualAmount(
                request.getTotalAmount(),
                request.getParticipants().size()
        )).willReturn(perPersonAmount);

        given(recurringSettlementManagementMapper.insert(
                any(RecurringSettlementDTO.class)
        )).willAnswer(invocation -> {
            RecurringSettlementDTO recurring =
                    invocation.getArgument(0);

            ReflectionTestUtils.setField(
                    recurring,
                    "recurringSettlementId",
                    RECURRING_SETTLEMENT_ID
            );

            return 1;
        });

        given(settlementMapper.insertSettlement(
                any(SettlementDTO.class)
        )).willAnswer(invocation -> {
            SettlementDTO settlement =
                    invocation.getArgument(0);

            settlement.setSettlementId(
                    FIRST_SETTLEMENT_ID
            );

            return 1;
        });

        given(settlementAccountService.selectAccount(
                OWNER_ID,
                FIRST_SETTLEMENT_ID,
                LINKED_ACCOUNT_ID
        )).willThrow(
                SettlementErrorCode
                        .SETTLEMENT_ACCOUNT_CREATE_FAILED
                        .toException()
        );

        assertThatThrownBy(() ->
                recurringSettlementService.create(
                        OWNER_ID,
                        request
                )
        ).isInstanceOfSatisfying(
                DomainException.class,
                exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(
                                        SettlementErrorCode.SETTLEMENT_ACCOUNT_CREATE_FAILED
                                )
        );

        then(participantRegistrationService)
                .should()
                .registerParticipants(
                        OWNER_ID,
                        FIRST_SETTLEMENT_ID,
                        request.getParticipants(),
                        perPersonAmount
                );
    }

    private CreateRecurringSettlementRequest createRequest() {
        return CreateRecurringSettlementRequest.builder()
                .settlementCategory("OTT·구독")
                .title("넷플릭스 구독")
                .totalAmount(new BigDecimal("450000"))
                .cycleRule(CycleRule.MONTHLY)
                .startDate(LocalDate.of(2026, 8, 17))
                .endDate(LocalDate.of(2027, 8, 17))
                .linkedAccountId(LINKED_ACCOUNT_ID)
                .participants(List.of(
                        participant("SAI_USER_A"),
                        participant("SAI_USER_B")
                ))
                .build();
    }

    private CreateSettlementParticipantRequest participant(
            String userToken
    ) {
        return CreateSettlementParticipantRequest.builder()
                .userToken(userToken)
                .build();
    }
}
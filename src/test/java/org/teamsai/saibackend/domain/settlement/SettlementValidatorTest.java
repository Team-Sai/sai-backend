package org.teamsai.saibackend.domain.settlement;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.teamsai.saibackend.domain.account.service.LinkedBankAccountService;
import org.teamsai.saibackend.domain.settlement.dto.SettlementDTO;
import org.teamsai.saibackend.domain.settlement.dto.request.CreateSettlementParticipantRequest;
import org.teamsai.saibackend.domain.settlement.dto.request.CreateSharedSettlementRequest;
import org.teamsai.saibackend.domain.settlement.exception.SettlementErrorCode;
import org.teamsai.saibackend.domain.settlement.mapper.SettlementParticipantMapper;
import org.teamsai.saibackend.domain.settlement.service.SettlementValidator;
import org.teamsai.saibackend.global.exception.DomainException;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("SettlementValidator 단위 테스트")
class SettlementValidatorTest {

    private static final Long SETTLEMENT_ID = 1L;
    private static final Long OWNER_ID = 10L;
    private static final Long MEMBER_ID = 20L;
    private static final Long OTHER_USER_ID = 30L;
    private static final Long LINKED_ACCOUNT_ID = 100L;

    @Mock
    private LinkedBankAccountService linkedBankAccountService;

    @Mock
    private SettlementParticipantMapper settlementParticipantMapper;

    @InjectMocks
    private SettlementValidator settlementValidator;


    @Test
    @DisplayName("정산 생성자는 OWNER 검증을 통과한다")
    void validateOwnerSuccess() {

        SettlementDTO settlement =
                settlement();

        settlementValidator.validateOwner(
                settlement,
                OWNER_ID
        );
    }


    @Test
    @DisplayName("정산 생성자가 아니면 OWNER 검증에 실패한다")
    void validateOwnerFailsWhenUserIsNotOwner() {

        SettlementDTO settlement =
                settlement();

        assertSettlementExceptionThrownBy(
                () ->
                        settlementValidator.validateOwner(
                                settlement,
                                OTHER_USER_ID
                        ),
                SettlementErrorCode.SETTLEMENT_ACCESS_DENIED
        );
    }


    @Test
    @DisplayName("본인에게 연동된 계좌이면 검증을 통과한다")
    void validateLinkedAccountOwnerSuccess() {

        given(
                linkedBankAccountService.isOwnedLinkedAccount(
                        OWNER_ID,
                        LINKED_ACCOUNT_ID
                )
        ).willReturn(true);

        settlementValidator.validateLinkedAccountOwner(
                OWNER_ID,
                LINKED_ACCOUNT_ID
        );

        verify(linkedBankAccountService)
                .isOwnedLinkedAccount(
                        OWNER_ID,
                        LINKED_ACCOUNT_ID
                );
    }


    @Test
    @DisplayName("본인에게 연동되지 않은 계좌이면 검증에 실패한다")
    void validateLinkedAccountOwnerFailsWhenAccountDoesNotBelongToUser() {

        given(
                linkedBankAccountService.isOwnedLinkedAccount(
                        OWNER_ID,
                        LINKED_ACCOUNT_ID
                )
        ).willReturn(false);

        assertSettlementExceptionThrownBy(
                () ->
                        settlementValidator
                                .validateLinkedAccountOwner(
                                        OWNER_ID,
                                        LINKED_ACCOUNT_ID
                                ),
                SettlementErrorCode.INVALID_SETTLEMENT_ACCOUNT
        );
    }


    @Test
    @DisplayName("정산 생성자는 정산 조회 권한 검증을 통과한다")
    void validateAccessibleUserSuccessForOwner() {

        SettlementDTO settlement =
                settlement();

        settlementValidator.validateAccessibleUser(
                settlement,
                OWNER_ID
        );

        verify(
                settlementParticipantMapper,
                never()
        ).existsActiveParticipant(
                SETTLEMENT_ID,
                OWNER_ID
        );
    }


    @Test
    @DisplayName("ACTIVE 참여자는 정산 조회 권한 검증을 통과한다")
    void validateAccessibleUserSuccessForMember() {

        SettlementDTO settlement =
                settlement();

        given(
                settlementParticipantMapper
                        .existsActiveParticipant(
                                SETTLEMENT_ID,
                                MEMBER_ID
                        )
        ).willReturn(true);

        settlementValidator.validateAccessibleUser(
                settlement,
                MEMBER_ID
        );

        verify(settlementParticipantMapper)
                .existsActiveParticipant(
                        SETTLEMENT_ID,
                        MEMBER_ID
                );
    }


    @Test
    @DisplayName("정산 생성자도 ACTIVE 참여자도 아니면 조회할 수 없다")
    void validateAccessibleUserFailsWhenUserHasNoAccess() {

        SettlementDTO settlement =
                settlement();

        given(
                settlementParticipantMapper
                        .existsActiveParticipant(
                                SETTLEMENT_ID,
                                OTHER_USER_ID
                        )
        ).willReturn(false);

        assertSettlementExceptionThrownBy(
                () ->
                        settlementValidator
                                .validateAccessibleUser(
                                        settlement,
                                        OTHER_USER_ID
                                ),
                SettlementErrorCode.SETTLEMENT_ACCESS_DENIED
        );

        verify(settlementParticipantMapper)
                .existsActiveParticipant(
                        SETTLEMENT_ID,
                        OTHER_USER_ID
                );
    }


    @Test
    @DisplayName("정상적인 공동정산 생성 요청은 검증을 통과한다")
    void validateCreateRequestSuccess() {

        CreateSharedSettlementRequest request =
                request(
                        List.of(
                                participant("SAI_USER_A"),
                                participant("SAI_USER_B")
                        )
                );

        settlementValidator.validateCreateRequest(
                request
        );
    }


    @Test
    @DisplayName("공동정산 생성 요청이 null이면 검증에 실패한다")
    void validateCreateRequestFailsWhenRequestIsNull() {

        assertSettlementExceptionThrownBy(
                () ->
                        settlementValidator
                                .validateCreateRequest(null),
                SettlementErrorCode.INVALID_SETTLEMENT_REQUEST
        );
    }


    @Test
    @DisplayName("참여자가 없으면 공동정산 생성 요청 검증에 실패한다")
    void validateCreateRequestFailsWhenParticipantsAreEmpty() {

        CreateSharedSettlementRequest request =
                request(List.of());

        assertSettlementExceptionThrownBy(
                () ->
                        settlementValidator
                                .validateCreateRequest(request),
                SettlementErrorCode.SETTLEMENT_PARTICIPANT_REQUIRED
        );
    }


    @Test
    @DisplayName("참여자 목록에 null이 포함되면 검증에 실패한다")
    void validateCreateRequestFailsWhenParticipantIsNull() {

        CreateSharedSettlementRequest request =
                request(
                        Arrays.asList(
                                participant("SAI_USER_A"),
                                null
                        )
                );

        assertThatThrownBy(
                () ->
                        settlementValidator
                                .validateCreateRequest(request)
        ).isInstanceOf(DomainException.class);
    }


    @Test
    @DisplayName("참여자 userToken이 비어 있으면 검증에 실패한다")
    void validateCreateRequestFailsWhenUserTokenIsBlank() {

        CreateSharedSettlementRequest request =
                request(
                        List.of(
                                participant(" ")
                        )
                );

        assertThatThrownBy(
                () ->
                        settlementValidator
                                .validateCreateRequest(request)
        ).isInstanceOf(DomainException.class);
    }


    @Test
    @DisplayName("동일한 userToken의 참여자가 중복되면 검증에 실패한다")
    void validateCreateRequestFailsWhenParticipantIsDuplicated() {

        CreateSharedSettlementRequest request =
                request(
                        List.of(
                                participant("SAI_USER_A"),
                                participant("SAI_USER_A")
                        )
                );

        assertThatThrownBy(
                () ->
                        settlementValidator
                                .validateCreateRequest(request)
        ).isInstanceOf(DomainException.class);
    }


    private SettlementDTO settlement() {

        return SettlementDTO.builder()
                .settlementId(SETTLEMENT_ID)
                .ownerId(OWNER_ID)
                .build();
    }


    private CreateSharedSettlementRequest request(
            List<CreateSettlementParticipantRequest> participants
    ) {

        return CreateSharedSettlementRequest.builder()
                .participants(participants)
                .build();
    }


    private CreateSettlementParticipantRequest participant(
            String userToken
    ) {

        return CreateSettlementParticipantRequest.builder()
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
                        exception ->
                                assertThat(
                                        exception.getErrorCode()
                                ).isEqualTo(errorCode)
                );
    }
}
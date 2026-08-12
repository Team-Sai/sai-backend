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
import org.teamsai.saibackend.domain.settlement.service.SettlementValidator;
import org.teamsai.saibackend.global.exception.DomainException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
@DisplayName("SettlementValidator 단위 테스트")
class SettlementValidatorTest {

    private static final Long OWNER_ID = 1L;
    private static final Long OTHER_USER_ID = 2L;
    private static final Long LINKED_ACCOUNT_ID = 10L;


    @Mock
    private LinkedBankAccountService linkedBankAccountService;

    @InjectMocks
    private SettlementValidator settlementValidator;


    @Test
    @DisplayName("정산 생성자이면 접근 검증을 통과한다")
    void validateOwnerSuccess() {

        SettlementDTO settlement =
                SettlementDTO.builder()
                        .ownerId(OWNER_ID)
                        .build();


        assertThatCode(
                () ->
                        settlementValidator.validateOwner(
                                settlement,
                                OWNER_ID
                        )
        ).doesNotThrowAnyException();
    }


    @Test
    @DisplayName("정산 생성자가 아니면 접근 검증에 실패한다")
    void validateOwnerFailsWhenUserIsNotOwner() {

        SettlementDTO settlement =
                SettlementDTO.builder()
                        .ownerId(OWNER_ID)
                        .build();


        assertSettlementExceptionThrownBy(
                () ->
                        settlementValidator.validateOwner(
                                settlement,
                                OTHER_USER_ID
                        ),
                SettlementErrorCode
                        .SETTLEMENT_ACCESS_DENIED
        );
    }


    @Test
    @DisplayName("본인에게 연동된 계좌이면 수취 계좌 검증을 통과한다")
    void validateLinkedAccountOwnerSuccess() {

        given(
                linkedBankAccountService.isOwnedLinkedAccount(
                        OWNER_ID,
                        LINKED_ACCOUNT_ID
                )
        ).willReturn(true);


        assertThatCode(
                () ->
                        settlementValidator
                                .validateLinkedAccountOwner(
                                        OWNER_ID,
                                        LINKED_ACCOUNT_ID
                                )
        ).doesNotThrowAnyException();
    }


    @Test
    @DisplayName("본인에게 연동되지 않은 계좌이면 수취 계좌 검증에 실패한다")
    void validateLinkedAccountOwnerFailsWhenAccountIsNotOwned() {

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
                SettlementErrorCode
                        .INVALID_SETTLEMENT_ACCOUNT
        );
    }


    @Test
    @DisplayName("정상적인 공동정산 생성 요청이면 검증을 통과한다")
    void validateCreateRequestSuccess() {

        CreateSharedSettlementRequest request =
                createRequest(
                        List.of(
                                participant("SAI_USER_A"),
                                participant("SAI_USER_B")
                        )
                );


        assertThatCode(
                () ->
                        settlementValidator
                                .validateCreateRequest(
                                        request
                                )
        ).doesNotThrowAnyException();
    }


    @Test
    @DisplayName("공동정산 생성 요청이 없으면 검증에 실패한다")
    void validateCreateRequestFailsWhenRequestIsNull() {

        assertSettlementExceptionThrownBy(
                () ->
                        settlementValidator
                                .validateCreateRequest(
                                        null
                                ),
                SettlementErrorCode
                        .INVALID_SETTLEMENT_REQUEST
        );
    }


    @Test
    @DisplayName("참여자가 없으면 공동정산 생성 요청 검증에 실패한다")
    void validateCreateRequestFailsWhenParticipantsAreEmpty() {

        CreateSharedSettlementRequest request =
                createRequest(
                        List.of()
                );


        assertSettlementExceptionThrownBy(
                () ->
                        settlementValidator
                                .validateCreateRequest(
                                        request
                                ),
                SettlementErrorCode
                        .SETTLEMENT_PARTICIPANT_REQUIRED
        );
    }


    @Test
    @DisplayName("참여자 정보가 null이면 공동정산 생성 요청 검증에 실패한다")
    void validateCreateRequestFailsWhenParticipantIsNull() {

        CreateSharedSettlementRequest request =
                createRequest(
                        java.util.Arrays.asList(
                                participant("SAI_USER_A"),
                                null
                        )
                );


        assertSettlementExceptionThrownBy(
                () ->
                        settlementValidator
                                .validateCreateRequest(
                                        request
                                ),
                SettlementErrorCode
                        .INVALID_SETTLEMENT_PARTICIPANT
        );
    }


    @Test
    @DisplayName("참여자 회원 코드가 비어 있으면 공동정산 생성 요청 검증에 실패한다")
    void validateCreateRequestFailsWhenUserTokenIsBlank() {

        CreateSharedSettlementRequest request =
                createRequest(
                        List.of(
                                participant(" ")
                        )
                );


        assertSettlementExceptionThrownBy(
                () ->
                        settlementValidator
                                .validateCreateRequest(
                                        request
                                ),
                SettlementErrorCode
                        .INVALID_SETTLEMENT_PARTICIPANT
        );
    }


    @Test
    @DisplayName("동일한 회원 코드를 중복 선택하면 공동정산 생성 요청 검증에 실패한다")
    void validateCreateRequestFailsWhenParticipantIsDuplicated() {

        CreateSharedSettlementRequest request =
                createRequest(
                        List.of(
                                participant("SAI_DUPLICATE"),
                                participant("SAI_DUPLICATE")
                        )
                );


        assertSettlementExceptionThrownBy(
                () ->
                        settlementValidator
                                .validateCreateRequest(
                                        request
                                ),
                SettlementErrorCode
                        .DUPLICATE_SETTLEMENT_PARTICIPANT
        );
    }


    private CreateSharedSettlementRequest createRequest(
            List<CreateSettlementParticipantRequest> participants
    ) {

        return CreateSharedSettlementRequest.builder()
                .settlementCategory("여행")
                .title("제주도 여행비 정산")
                .totalAmount(
                        new BigDecimal("30000")
                )
                .dueDate(
                        LocalDate.now().plusDays(7)
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

        return CreateSettlementParticipantRequest.builder()
                .userToken(userToken)
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
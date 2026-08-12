package org.teamsai.saibackend.domain.settlement;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.teamsai.saibackend.domain.account.dto.response.LinkedBankAccountResponse;
import org.teamsai.saibackend.domain.account.service.LinkedBankAccountService;
import org.teamsai.saibackend.domain.settlement.dto.SettlementAccountDTO;
import org.teamsai.saibackend.domain.settlement.dto.SettlementDTO;
import org.teamsai.saibackend.domain.settlement.dto.request.SelectSettlementAccountRequest;
import org.teamsai.saibackend.domain.settlement.dto.response.SettlementAccountResponse;
import org.teamsai.saibackend.domain.settlement.exception.SettlementErrorCode;
import org.teamsai.saibackend.domain.settlement.mapper.SettlementAccountMapper;
import org.teamsai.saibackend.domain.settlement.mapper.SettlementMapper;
import org.teamsai.saibackend.domain.settlement.service.SettlementAccountService;
import org.teamsai.saibackend.domain.settlement.service.SettlementValidator;
import org.teamsai.saibackend.domain.settlement.type.SettlementAccountStatus;
import org.teamsai.saibackend.global.exception.DomainException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("SettlementAccountService 단위 테스트")
class SettlementAccountServiceTest {

    private static final Long OWNER_ID = 3L;
    private static final Long OTHER_USER_ID = 4L;

    private static final Long SETTLEMENT_ID = 6L;

    private static final Long FIRST_LINKED_ACCOUNT_ID = 2L;
    private static final Long SECOND_LINKED_ACCOUNT_ID = 3L;

    private static final Long FIRST_SETTLEMENT_ACCOUNT_ID = 1L;
    private static final Long SECOND_SETTLEMENT_ACCOUNT_ID = 2L;

    @Mock
    private SettlementMapper settlementMapper;

    @Mock
    private SettlementAccountMapper settlementAccountMapper;

    @Mock
    private LinkedBankAccountService linkedBankAccountService;

    @Mock
    private SettlementValidator settlementValidator;

    @InjectMocks
    private SettlementAccountService settlementAccountService;


    @Nested
    @DisplayName("정산 수취 계좌 설정")
    class SelectAccount {

        @Test
        @DisplayName("수취 계좌가 없으면 선택한 연동 계좌를 ACTIVE 상태로 등록한다")
        void selectAccountSuccessWhenCurrentAccountDoesNotExist() {

            SettlementDTO settlement =
                    createSettlement(OWNER_ID);

            SelectSettlementAccountRequest request =
                    createRequest(FIRST_LINKED_ACCOUNT_ID);

            LinkedBankAccountResponse linkedAccount =
                    linkedAccount(FIRST_LINKED_ACCOUNT_ID);

            when(settlementMapper.findById(SETTLEMENT_ID))
                    .thenReturn(Optional.of(settlement));

            when(
                    settlementAccountMapper
                            .findActiveBySettlementIdForUpdate(
                                    SETTLEMENT_ID
                            )
            ).thenReturn(Optional.empty());

            when(
                    settlementAccountMapper.insert(
                            any(SettlementAccountDTO.class)
                    )
            ).thenAnswer(invocation -> {

                SettlementAccountDTO account =
                        invocation.getArgument(0);

                ReflectionTestUtils.setField(
                        account,
                        "settlementAccountId",
                        FIRST_SETTLEMENT_ACCOUNT_ID
                );

                return 1;
            });

            when(
                    linkedBankAccountService
                            .getLinkedAccounts(OWNER_ID)
            ).thenReturn(
                    List.of(linkedAccount)
            );


            SettlementAccountResponse response =
                    settlementAccountService.selectAccount(
                            OWNER_ID,
                            SETTLEMENT_ID,
                            request.getLinkedAccountId()
                    );


            ArgumentCaptor<SettlementAccountDTO> captor =
                    ArgumentCaptor.forClass(
                            SettlementAccountDTO.class
                    );

            verify(settlementAccountMapper)
                    .insert(captor.capture());

            SettlementAccountDTO savedAccount =
                    captor.getValue();

            assertThat(savedAccount.getSettlementId())
                    .isEqualTo(SETTLEMENT_ID);

            assertThat(savedAccount.getLinkedAccountId())
                    .isEqualTo(FIRST_LINKED_ACCOUNT_ID);

            assertThat(savedAccount.getAccountStatus())
                    .isEqualTo(
                            SettlementAccountStatus.ACTIVE
                    );

            assertThat(savedAccount.getSelectedAt())
                    .isNotNull();

            assertThat(savedAccount.getEndedAt())
                    .isNull();

            assertThat(response.getSettlementAccountId())
                    .isEqualTo(
                            FIRST_SETTLEMENT_ACCOUNT_ID
                    );

            assertThat(response.getSettlementId())
                    .isEqualTo(SETTLEMENT_ID);

            assertThat(response.getLinkedAccountId())
                    .isEqualTo(
                            FIRST_LINKED_ACCOUNT_ID
                    );

            assertThat(response.getAccountStatus())
                    .isEqualTo(
                            SettlementAccountStatus.ACTIVE
                    );

            verify(settlementValidator)
                    .validateOwner(
                            settlement,
                            OWNER_ID
                    );

            verify(settlementValidator)
                    .validateLinkedAccountOwner(
                            OWNER_ID,
                            FIRST_LINKED_ACCOUNT_ID
                    );
        }


        @Test
        @DisplayName("현재 수취 계좌와 같은 계좌를 다시 선택하면 새 행을 생성하지 않는다")
        void selectSameAccountDoesNotInsertAgain() {

            SettlementDTO settlement =
                    createSettlement(OWNER_ID);

            SettlementAccountDTO currentAccount =
                    createActiveSettlementAccount(
                            FIRST_SETTLEMENT_ACCOUNT_ID,
                            FIRST_LINKED_ACCOUNT_ID
                    );

            SelectSettlementAccountRequest request =
                    createRequest(FIRST_LINKED_ACCOUNT_ID);

            LinkedBankAccountResponse linkedAccount =
                    linkedAccount(FIRST_LINKED_ACCOUNT_ID);

            when(settlementMapper.findById(SETTLEMENT_ID))
                    .thenReturn(Optional.of(settlement));

            when(
                    settlementAccountMapper
                            .findActiveBySettlementIdForUpdate(
                                    SETTLEMENT_ID
                            )
            ).thenReturn(
                    Optional.of(currentAccount)
            );

            when(
                    linkedBankAccountService
                            .getLinkedAccounts(OWNER_ID)
            ).thenReturn(
                    List.of(linkedAccount)
            );


            SettlementAccountResponse response =
                    settlementAccountService.selectAccount(
                            OWNER_ID,
                            SETTLEMENT_ID,
                            request.getLinkedAccountId()
                    );


            assertThat(response.getSettlementAccountId())
                    .isEqualTo(
                            FIRST_SETTLEMENT_ACCOUNT_ID
                    );

            assertThat(response.getLinkedAccountId())
                    .isEqualTo(
                            FIRST_LINKED_ACCOUNT_ID
                    );

            verify(settlementValidator)
                    .validateOwner(
                            settlement,
                            OWNER_ID
                    );

            verify(settlementValidator)
                    .validateLinkedAccountOwner(
                            OWNER_ID,
                            FIRST_LINKED_ACCOUNT_ID
                    );

            verify(settlementAccountMapper, never())
                    .updateStatus(
                            any(),
                            any(),
                            any()
                    );

            verify(settlementAccountMapper, never())
                    .insert(any());
        }


        @Test
        @DisplayName("다른 계좌로 변경하면 기존 계좌를 REPLACED로 변경하고 새 ACTIVE 계좌를 등록한다")
        void replaceSettlementAccountSuccess() {

            SettlementDTO settlement =
                    createSettlement(OWNER_ID);

            SettlementAccountDTO currentAccount =
                    createActiveSettlementAccount(
                            FIRST_SETTLEMENT_ACCOUNT_ID,
                            FIRST_LINKED_ACCOUNT_ID
                    );

            SelectSettlementAccountRequest request =
                    createRequest(
                            SECOND_LINKED_ACCOUNT_ID
                    );

            LinkedBankAccountResponse secondLinkedAccount =
                    linkedAccount(
                            SECOND_LINKED_ACCOUNT_ID
                    );

            when(settlementMapper.findById(SETTLEMENT_ID))
                    .thenReturn(
                            Optional.of(settlement)
                    );

            when(
                    settlementAccountMapper
                            .findActiveBySettlementIdForUpdate(
                                    SETTLEMENT_ID
                            )
            ).thenReturn(
                    Optional.of(currentAccount)
            );

            when(
                    settlementAccountMapper.updateStatus(
                            eq(FIRST_SETTLEMENT_ACCOUNT_ID),
                            eq(SettlementAccountStatus.REPLACED),
                            any(LocalDateTime.class)
                    )
            ).thenReturn(1);

            when(
                    settlementAccountMapper.insert(
                            any(SettlementAccountDTO.class)
                    )
            ).thenAnswer(invocation -> {

                SettlementAccountDTO account =
                        invocation.getArgument(0);

                ReflectionTestUtils.setField(
                        account,
                        "settlementAccountId",
                        SECOND_SETTLEMENT_ACCOUNT_ID
                );

                return 1;
            });

            when(
                    linkedBankAccountService
                            .getLinkedAccounts(OWNER_ID)
            ).thenReturn(
                    List.of(
                            secondLinkedAccount
                    )
            );


            SettlementAccountResponse response =
                    settlementAccountService.selectAccount(
                            OWNER_ID,
                            SETTLEMENT_ID,
                            request.getLinkedAccountId()
                    );


            ArgumentCaptor<LocalDateTime> endedAtCaptor =
                    ArgumentCaptor.forClass(
                            LocalDateTime.class
                    );

            verify(settlementAccountMapper)
                    .updateStatus(
                            eq(FIRST_SETTLEMENT_ACCOUNT_ID),
                            eq(SettlementAccountStatus.REPLACED),
                            endedAtCaptor.capture()
                    );

            assertThat(endedAtCaptor.getValue())
                    .isNotNull();

            ArgumentCaptor<SettlementAccountDTO> accountCaptor =
                    ArgumentCaptor.forClass(
                            SettlementAccountDTO.class
                    );

            verify(settlementAccountMapper)
                    .insert(
                            accountCaptor.capture()
                    );

            SettlementAccountDTO newAccount =
                    accountCaptor.getValue();

            assertThat(newAccount.getLinkedAccountId())
                    .isEqualTo(
                            SECOND_LINKED_ACCOUNT_ID
                    );

            assertThat(newAccount.getAccountStatus())
                    .isEqualTo(
                            SettlementAccountStatus.ACTIVE
                    );

            assertThat(newAccount.getEndedAt())
                    .isNull();

            assertThat(response.getLinkedAccountId())
                    .isEqualTo(
                            SECOND_LINKED_ACCOUNT_ID
                    );

            verify(settlementValidator)
                    .validateOwner(
                            settlement,
                            OWNER_ID
                    );

            verify(settlementValidator)
                    .validateLinkedAccountOwner(
                            OWNER_ID,
                            SECOND_LINKED_ACCOUNT_ID
                    );
        }

        @Test
        @DisplayName("정산 생성자가 아니면 수취 계좌를 설정할 수 없다")
        void selectAccountFailsWhenUserIsNotOwner() {

            SettlementDTO settlement =
                    createSettlement(OWNER_ID);

            SelectSettlementAccountRequest request =
                    createRequest(
                            FIRST_LINKED_ACCOUNT_ID
                    );

            when(settlementMapper.findById(SETTLEMENT_ID))
                    .thenReturn(
                            Optional.of(settlement)
                    );

            doThrow(
                    SettlementErrorCode
                            .SETTLEMENT_ACCESS_DENIED
                            .toException()
            ).when(settlementValidator)
                    .validateOwner(
                            settlement,
                            OTHER_USER_ID
                    );


            assertThatThrownBy(
                    () ->
                            settlementAccountService
                                    .selectAccount(
                                            OTHER_USER_ID,
                                            SETTLEMENT_ID,
                                            request.getLinkedAccountId()
                                    )
            ).isInstanceOf(
                    DomainException.class
            );


            verify(settlementValidator)
                    .validateOwner(
                            settlement,
                            OTHER_USER_ID
                    );

            verify(
                    settlementValidator,
                    never()
            ).validateLinkedAccountOwner(
                    any(),
                    any()
            );

            verifyNoInteractions(
                    settlementAccountMapper
            );
        }


        @Test
        @DisplayName("본인에게 연동되지 않은 계좌는 정산 수취 계좌로 설정할 수 없다")
        void selectAccountFailsWhenLinkedAccountDoesNotBelongToUser() {

            SettlementDTO settlement =
                    createSettlement(OWNER_ID);

            SelectSettlementAccountRequest request =
                    createRequest(
                            SECOND_LINKED_ACCOUNT_ID
                    );

            when(settlementMapper.findById(SETTLEMENT_ID))
                    .thenReturn(
                            Optional.of(settlement)
                    );

            doThrow(
                    SettlementErrorCode
                            .INVALID_SETTLEMENT_ACCOUNT
                            .toException()
            ).when(settlementValidator)
                    .validateLinkedAccountOwner(
                            OWNER_ID,
                            SECOND_LINKED_ACCOUNT_ID
                    );


            assertThatThrownBy(
                    () ->
                            settlementAccountService
                                    .selectAccount(
                                            OWNER_ID,
                                            SETTLEMENT_ID,
                                            request.getLinkedAccountId()
                                    )
            ).isInstanceOf(
                    DomainException.class
            );


            verify(settlementValidator)
                    .validateOwner(
                            settlement,
                            OWNER_ID
                    );

            verify(settlementValidator)
                    .validateLinkedAccountOwner(
                            OWNER_ID,
                            SECOND_LINKED_ACCOUNT_ID
                    );

            verifyNoInteractions(
                    settlementAccountMapper
            );
        }


        @Test
        @DisplayName("존재하지 않는 정산에는 수취 계좌를 설정할 수 없다")
        void selectAccountFailsWhenSettlementDoesNotExist() {

            SelectSettlementAccountRequest request =
                    createRequest(
                            FIRST_LINKED_ACCOUNT_ID
                    );

            when(settlementMapper.findById(SETTLEMENT_ID))
                    .thenReturn(
                            Optional.empty()
                    );


            assertThatThrownBy(
                    () ->
                            settlementAccountService
                                    .selectAccount(
                                            OWNER_ID,
                                            SETTLEMENT_ID,
                                            request.getLinkedAccountId()
                                    )
            ).isInstanceOf(
                    DomainException.class
            );


            verifyNoInteractions(
                    settlementValidator,
                    settlementAccountMapper
            );
        }


        @Test
        @DisplayName("기존 수취 계좌 상태 변경에 실패하면 새 계좌를 등록하지 않는다")
        void replaceAccountFailsWhenCurrentAccountUpdateFails() {

            SettlementDTO settlement =
                    createSettlement(OWNER_ID);

            SettlementAccountDTO currentAccount =
                    createActiveSettlementAccount(
                            FIRST_SETTLEMENT_ACCOUNT_ID,
                            FIRST_LINKED_ACCOUNT_ID
                    );

            SelectSettlementAccountRequest request =
                    createRequest(
                            SECOND_LINKED_ACCOUNT_ID
                    );

            when(settlementMapper.findById(SETTLEMENT_ID))
                    .thenReturn(
                            Optional.of(settlement)
                    );

            when(
                    settlementAccountMapper
                            .findActiveBySettlementIdForUpdate(
                                    SETTLEMENT_ID
                            )
            ).thenReturn(
                    Optional.of(currentAccount)
            );

            when(
                    settlementAccountMapper.updateStatus(
                            eq(FIRST_SETTLEMENT_ACCOUNT_ID),
                            eq(SettlementAccountStatus.REPLACED),
                            any(LocalDateTime.class)
                    )
            ).thenReturn(0);


            assertThatThrownBy(
                    () ->
                            settlementAccountService
                                    .selectAccount(
                                            OWNER_ID,
                                            SETTLEMENT_ID,
                                            request.getLinkedAccountId()
                                    )
            ).isInstanceOf(
                    DomainException.class
            );


            verify(settlementValidator)
                    .validateOwner(
                            settlement,
                            OWNER_ID
                    );

            verify(settlementValidator)
                    .validateLinkedAccountOwner(
                            OWNER_ID,
                            SECOND_LINKED_ACCOUNT_ID
                    );

            verify(settlementAccountMapper, never())
                    .insert(any());
        }


        @Test
        @DisplayName("새 수취 계좌 저장에 실패하면 예외가 발생한다")
        void selectAccountFailsWhenInsertFails() {

            SettlementDTO settlement =
                    createSettlement(OWNER_ID);

            SelectSettlementAccountRequest request =
                    createRequest(
                            FIRST_LINKED_ACCOUNT_ID
                    );

            when(settlementMapper.findById(SETTLEMENT_ID))
                    .thenReturn(
                            Optional.of(settlement)
                    );

            when(
                    settlementAccountMapper
                            .findActiveBySettlementIdForUpdate(
                                    SETTLEMENT_ID
                            )
            ).thenReturn(
                    Optional.empty()
            );

            when(
                    settlementAccountMapper.insert(
                            any(SettlementAccountDTO.class)
                    )
            ).thenReturn(0);


            assertThatThrownBy(
                    () ->
                            settlementAccountService
                                    .selectAccount(
                                            OWNER_ID,
                                            SETTLEMENT_ID,
                                            request.getLinkedAccountId()
                                    )
            ).isInstanceOf(
                    DomainException.class
            );


            verify(settlementValidator)
                    .validateOwner(
                            settlement,
                            OWNER_ID
                    );

            verify(settlementValidator)
                    .validateLinkedAccountOwner(
                            OWNER_ID,
                            FIRST_LINKED_ACCOUNT_ID
                    );
        }
    }


    @Nested
    @DisplayName("현재 정산 수취 계좌 조회")
    class FindCurrentAccount {

        @Test
        @DisplayName("현재 ACTIVE 상태의 수취 계좌를 조회한다")
        void findCurrentAccountSuccess() {

            SettlementDTO settlement =
                    createSettlement(OWNER_ID);

            SettlementAccountDTO account =
                    createActiveSettlementAccount(
                            SECOND_SETTLEMENT_ACCOUNT_ID,
                            SECOND_LINKED_ACCOUNT_ID
                    );

            LinkedBankAccountResponse linkedAccount =
                    linkedAccount(SECOND_LINKED_ACCOUNT_ID);

            when(settlementMapper.findById(SETTLEMENT_ID))
                    .thenReturn(
                            Optional.of(settlement)
                    );

            when(
                    settlementAccountMapper
                            .findActiveBySettlementId(
                                    SETTLEMENT_ID
                            )
            ).thenReturn(
                    Optional.of(account)
            );

            when(
                    linkedBankAccountService
                            .getLinkedAccounts(OWNER_ID)
            ).thenReturn(
                    List.of(linkedAccount)
            );


            SettlementAccountResponse response =
                    settlementAccountService
                            .findCurrentAccount(
                                    OWNER_ID,
                                    SETTLEMENT_ID
                            );


            assertThat(response.getSettlementAccountId())
                    .isEqualTo(
                            SECOND_SETTLEMENT_ACCOUNT_ID
                    );

            assertThat(response.getSettlementId())
                    .isEqualTo(
                            SETTLEMENT_ID
                    );

            assertThat(response.getLinkedAccountId())
                    .isEqualTo(
                            SECOND_LINKED_ACCOUNT_ID
                    );

            assertThat(response.getAccountStatus())
                    .isEqualTo(
                            SettlementAccountStatus.ACTIVE
                    );

            verify(settlementValidator)
                    .validateOwner(
                            settlement,
                            OWNER_ID
                    );
        }


        @Test
        @DisplayName("현재 설정된 수취 계좌가 없으면 예외가 발생한다")
        void findCurrentAccountFailsWhenAccountDoesNotExist() {

            SettlementDTO settlement =
                    createSettlement(OWNER_ID);

            when(settlementMapper.findById(SETTLEMENT_ID))
                    .thenReturn(
                            Optional.of(settlement)
                    );

            when(
                    settlementAccountMapper
                            .findActiveBySettlementId(
                                    SETTLEMENT_ID
                            )
            ).thenReturn(
                    Optional.empty()
            );


            assertThatThrownBy(
                    () ->
                            settlementAccountService
                                    .findCurrentAccount(
                                            OWNER_ID,
                                            SETTLEMENT_ID
                                    )
            ).isInstanceOf(
                    DomainException.class
            );


            verify(settlementValidator)
                    .validateOwner(
                            settlement,
                            OWNER_ID
                    );
        }


        @Test
        @DisplayName("정산 생성자가 아니면 현재 수취 계좌를 조회할 수 없다")
        void findCurrentAccountFailsWhenUserIsNotOwner() {

            SettlementDTO settlement =
                    createSettlement(OWNER_ID);

            when(settlementMapper.findById(SETTLEMENT_ID))
                    .thenReturn(
                            Optional.of(settlement)
                    );

            doThrow(
                    SettlementErrorCode
                            .SETTLEMENT_ACCESS_DENIED
                            .toException()
            ).when(settlementValidator)
                    .validateOwner(
                            settlement,
                            OTHER_USER_ID
                    );


            assertThatThrownBy(
                    () ->
                            settlementAccountService
                                    .findCurrentAccount(
                                            OTHER_USER_ID,
                                            SETTLEMENT_ID
                                    )
            ).isInstanceOf(
                    DomainException.class
            );


            verify(settlementValidator)
                    .validateOwner(
                            settlement,
                            OTHER_USER_ID
                    );

            verify(
                    settlementAccountMapper,
                    never()
            ).findActiveBySettlementId(
                    SETTLEMENT_ID
            );
        }
    }


    private SettlementDTO createSettlement(
            Long ownerId
    ) {

        return SettlementDTO.builder()
                .settlementId(SETTLEMENT_ID)
                .ownerId(ownerId)
                .build();
    }


    private SelectSettlementAccountRequest createRequest(
            Long linkedAccountId
    ) {

        return SelectSettlementAccountRequest.builder()
                .linkedAccountId(linkedAccountId)
                .build();
    }


    private SettlementAccountDTO createActiveSettlementAccount(
            Long settlementAccountId,
            Long linkedAccountId
    ) {

        return SettlementAccountDTO.builder()
                .settlementAccountId(
                        settlementAccountId
                )
                .settlementId(
                        SETTLEMENT_ID
                )
                .linkedAccountId(
                        linkedAccountId
                )
                .accountStatus(
                        SettlementAccountStatus.ACTIVE
                )
                .selectedAt(
                        LocalDateTime.now()
                                .minusHours(1)
                )
                .endedAt(null)
                .build();
    }


    private LinkedBankAccountResponse linkedAccount(
            Long linkedAccountId
    ) {

        LinkedBankAccountResponse linkedAccount =
                mock(LinkedBankAccountResponse.class);

        when(linkedAccount.linkedAccountId())
                .thenReturn(linkedAccountId);

        when(linkedAccount.bankName())
                .thenReturn("테스트은행");

        when(linkedAccount.maskedAccountNumber())
                .thenReturn("123-****-4567");

        when(linkedAccount.accountHolderName())
                .thenReturn("홍길동");

        return linkedAccount;
    }
}
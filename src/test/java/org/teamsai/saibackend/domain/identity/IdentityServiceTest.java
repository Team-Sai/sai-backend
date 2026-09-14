package org.teamsai.saibackend.domain.identity;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.teamsai.saibackend.domain.identity.dto.IdentityDTO;
import org.teamsai.saibackend.domain.identity.dto.request.IdentityPrepareRequest;
import org.teamsai.saibackend.domain.identity.dto.response.IdentityCompleteResponse;
import org.teamsai.saibackend.domain.identity.dto.response.IdentityPrepareResponse;
import org.teamsai.saibackend.domain.identity.dto.response.PortOneIdentityResponse;
import org.teamsai.saibackend.domain.identity.exception.IdentityErrorCode;
import org.teamsai.saibackend.domain.identity.mapper.IdentityMapper;
import org.teamsai.saibackend.domain.identity.service.IdentityService;
import org.teamsai.saibackend.domain.identity.service.IdentityValidator;
import org.teamsai.saibackend.domain.identity.service.PortOneIdentityService;
import org.teamsai.saibackend.domain.identity.type.IdentityPurpose;
import org.teamsai.saibackend.domain.identity.type.IdentityStatus;
import org.teamsai.saibackend.domain.user.dto.UserDTO;
import org.teamsai.saibackend.domain.user.exception.UserErrorCode;
import org.teamsai.saibackend.domain.user.mapper.UserMapper;
import org.teamsai.saibackend.global.exception.DomainException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
@DisplayName("IdentityService 단위 테스트")
class IdentityServiceTest {

    private static final Long USER_ID = 2L;
    private static final Long OTHER_USER_ID = 3L;

    private static final String STORE_ID = "store-test";
    private static final String CHANNEL_KEY = "channel-test";
    private static final long VALID_MINUTES = 10L;

    private static final String VERIFICATION_ID =
            "identity-verification-test1234";

    private static final String USER_NAME = "김사이";

    private static final LocalDate BIRTH_DATE =
            LocalDate.of(2000, 01, 01);

    @Mock
    private IdentityMapper identityMapper;

    @Mock
    private UserMapper userMapper;

    @Mock
    private PortOneIdentityService portOneIdentityService;

    @Mock
    private IdentityValidator identityValidator;

    private IdentityService identityService;

    @BeforeEach
    void setUp() {
        identityService = new IdentityService(
                identityMapper,
                userMapper,
                portOneIdentityService,
                identityValidator,
                STORE_ID,
                CHANNEL_KEY,
                VALID_MINUTES
        );
    }

    @Nested
    @DisplayName("본인인증 요청 준비")
    class Prepare {

        @Test
        @DisplayName("REQUESTED 상태의 인증 요청을 저장하고 SDK 정보를 반환한다")
        void prepareSuccess() {
            IdentityPrepareRequest request =
                    new IdentityPrepareRequest(
                            IdentityPurpose.LOAN_CONTRACT
                    );

            given(identityMapper.insert(any(IdentityDTO.class)))
                    .willReturn(1);

            IdentityPrepareResponse response =
                    identityService.prepare(
                            USER_ID,
                            request
                    );

            ArgumentCaptor<IdentityDTO> captor =
                    ArgumentCaptor.forClass(IdentityDTO.class);

            verify(identityMapper)
                    .insert(captor.capture());

            IdentityDTO savedIdentity =
                    captor.getValue();

            assertThat(savedIdentity.getUserId())
                    .isEqualTo(USER_ID);

            assertThat(savedIdentity.getPurpose())
                    .isEqualTo(
                            IdentityPurpose.LOAN_CONTRACT
                    );

            assertThat(savedIdentity.getStatus())
                    .isEqualTo(
                            IdentityStatus.REQUESTED
                    );

            assertThat(savedIdentity.getRequestedAt())
                    .isNotNull();

            assertThat(savedIdentity.getIdentityVerificationId())
                    .startsWith("identity-verification-");

            assertThat(response.identityVerificationId())
                    .isEqualTo(
                            savedIdentity.getIdentityVerificationId()
                    );

            assertThat(response.storeId())
                    .isEqualTo(STORE_ID);

            assertThat(response.channelKey())
                    .isEqualTo(CHANNEL_KEY);
        }

        @Test
        @DisplayName("인증 요청 저장 결과가 1건이 아니면 예외가 발생한다")
        void prepareFailsWhenInsertFails() {
            IdentityPrepareRequest request =
                    new IdentityPrepareRequest(
                            IdentityPurpose.LOAN_CONTRACT
                    );

            given(identityMapper.insert(any(IdentityDTO.class)))
                    .willReturn(0);

            assertIdentityError(
                    () -> identityService.prepare(
                            USER_ID,
                            request
                    ),
                    IdentityErrorCode
                            .IDENTITY_VERIFICATION_CREATE_FAILED
            );
        }

        @Test
        @DisplayName("userId가 없으면 인증 요청을 생성하지 않는다")
        void prepareFailsWithoutUserId() {
            IdentityPrepareRequest request =
                    new IdentityPrepareRequest(
                            IdentityPurpose.LOAN_CONTRACT
                    );

            assertIdentityError(
                    () -> identityService.prepare(
                            null,
                            request
                    ),
                    IdentityErrorCode.UNAUTHENTICATED_USER
            );

            verify(identityMapper, never())
                    .insert(any(IdentityDTO.class));
        }
    }

    @Nested
    @DisplayName("본인인증 완료")
    class Complete {

        @Test
        @DisplayName("포트원 인증자와 회원정보가 일치하면 VERIFIED로 변경한다")
        void completeSuccess() {
            IdentityDTO identity =
                    createRequestedIdentity(USER_ID);

            UserDTO user = createUser();

            PortOneIdentityResponse response =
                    createVerifiedPortOneResponse();

            given(
                    identityMapper
                            .findByIdentityVerificationId(
                                    VERIFICATION_ID
                            )
            ).willReturn(identity);

            given(
                    portOneIdentityService
                            .getIdentityVerification(
                                    VERIFICATION_ID
                            )
            ).willReturn(response);

            given(userMapper.findById(USER_ID))
                    .willReturn(Optional.of(user));

            given(
                    identityMapper.updateVerified(
                            eq(VERIFICATION_ID),
                            any(LocalDateTime.class),
                            any(LocalDateTime.class)
                    )
            ).willReturn(1);

            IdentityCompleteResponse result =
                    identityService.complete(
                            USER_ID,
                            VERIFICATION_ID
                    );

            ArgumentCaptor<LocalDateTime> verifiedAtCaptor =
                    ArgumentCaptor.forClass(
                            LocalDateTime.class
                    );

            ArgumentCaptor<LocalDateTime> expiresAtCaptor =
                    ArgumentCaptor.forClass(
                            LocalDateTime.class
                    );

            verify(identityValidator)
                    .validatePortOneResponse(
                            VERIFICATION_ID,
                            response
                    );

            verify(identityValidator)
                    .validateSameUser(
                            user,
                            response.verifiedCustomer()
                    );

            verify(identityMapper)
                    .updateVerified(
                            eq(VERIFICATION_ID),
                            verifiedAtCaptor.capture(),
                            expiresAtCaptor.capture()
                    );

            assertThat(result.status())
                    .isEqualTo(IdentityStatus.VERIFIED);

            assertThat(result.identityVerificationId())
                    .isEqualTo(VERIFICATION_ID);

            assertThat(expiresAtCaptor.getValue())
                    .isEqualTo(
                            verifiedAtCaptor
                                    .getValue()
                                    .plusMinutes(VALID_MINUTES)
                    );
        }

        @Test
        @DisplayName("다른 회원의 인증 요청에는 접근할 수 없다")
        void completeFailsWhenOwnerIsDifferent() {
            IdentityDTO identity =
                    createRequestedIdentity(
                            OTHER_USER_ID
                    );

            given(
                    identityMapper
                            .findByIdentityVerificationId(
                                    VERIFICATION_ID
                            )
            ).willReturn(identity);

            assertIdentityError(
                    () -> identityService.complete(
                            USER_ID,
                            VERIFICATION_ID
                    ),
                    IdentityErrorCode
                            .IDENTITY_VERIFICATION_FORBIDDEN
            );

            verifyNoInteractions(
                    portOneIdentityService,
                    identityValidator,
                    userMapper
            );
        }

        @Test
        @DisplayName("이미 VERIFIED인 인증은 기존 결과를 반환한다")
        void completeReturnsExistingVerifiedResult() {
            LocalDateTime verifiedAt =
                    LocalDateTime.of(
                            2026,
                            8,
                            2,
                            18,
                            0
                    );

            LocalDateTime expiresAt =
                    verifiedAt.plusMinutes(
                            VALID_MINUTES
                    );

            IdentityDTO identity =
                    IdentityDTO.builder()
                            .identityVerificationId(
                                    VERIFICATION_ID
                            )
                            .userId(USER_ID)
                            .purpose(
                                    IdentityPurpose.LOAN_CONTRACT
                            )
                            .status(
                                    IdentityStatus.VERIFIED
                            )
                            .verifiedAt(verifiedAt)
                            .expiresAt(expiresAt)
                            .build();

            given(
                    identityMapper
                            .findByIdentityVerificationId(
                                    VERIFICATION_ID
                            )
            ).willReturn(identity);

            IdentityCompleteResponse result =
                    identityService.complete(
                            USER_ID,
                            VERIFICATION_ID
                    );

            assertThat(result.status())
                    .isEqualTo(IdentityStatus.VERIFIED);

            assertThat(result.verifiedAt())
                    .isEqualTo(verifiedAt);

            assertThat(result.expiresAt())
                    .isEqualTo(expiresAt);

            verifyNoInteractions(
                    portOneIdentityService,
                    identityValidator,
                    userMapper
            );
        }

        @Test
        @DisplayName("포트원 상태가 FAILED이면 로컬 인증도 FAILED로 변경한다")
        void completeFailsWhenPortOneStatusIsFailed() {
            IdentityDTO identity =
                    createRequestedIdentity(USER_ID);

            PortOneIdentityResponse response =
                    new PortOneIdentityResponse(
                            VERIFICATION_ID,
                            "FAILED",
                            null,
                            new PortOneIdentityResponse.Failure(
                                    "인증 실패",
                                    "PG-001",
                                    "사용자 인증 실패"
                            )
                    );

            given(
                    identityMapper
                            .findByIdentityVerificationId(
                                    VERIFICATION_ID
                            )
            ).willReturn(identity);

            given(
                    portOneIdentityService
                            .getIdentityVerification(
                                    VERIFICATION_ID
                            )
            ).willReturn(response);

            given(
                    identityMapper.updateFailed(
                            VERIFICATION_ID,
                            "인증 실패 | PG-001 | 사용자 인증 실패"
                    )
            ).willReturn(1);

            assertIdentityError(
                    () -> identityService.complete(
                            USER_ID,
                            VERIFICATION_ID
                    ),
                    IdentityErrorCode
                            .PORTONE_VERIFICATION_NOT_VERIFIED
            );

            verify(identityMapper)
                    .updateFailed(
                            VERIFICATION_ID,
                            "인증 실패 | PG-001 | 사용자 인증 실패"
                    );

            verify(identityMapper, never())
                    .updateVerified(
                            any(),
                            any(),
                            any()
                    );

            verifyNoInteractions(userMapper);
        }

        @Test
        @DisplayName("포트원 상태가 READY이면 DB 상태를 변경하지 않는다")
        void completeFailsWhenVerificationIsNotCompleted() {
            IdentityDTO identity =
                    createRequestedIdentity(USER_ID);

            PortOneIdentityResponse response =
                    new PortOneIdentityResponse(
                            VERIFICATION_ID,
                            "READY",
                            null,
                            null
                    );

            given(
                    identityMapper
                            .findByIdentityVerificationId(
                                    VERIFICATION_ID
                            )
            ).willReturn(identity);

            given(
                    portOneIdentityService
                            .getIdentityVerification(
                                    VERIFICATION_ID
                            )
            ).willReturn(response);

            assertIdentityError(
                    () -> identityService.complete(
                            USER_ID,
                            VERIFICATION_ID
                    ),
                    IdentityErrorCode
                            .IDENTITY_VERIFICATION_NOT_COMPLETED
            );

            verify(identityMapper, never())
                    .updateFailed(
                            any(),
                            any()
                    );

            verify(identityMapper, never())
                    .updateVerified(
                            any(),
                            any(),
                            any()
                    );

            verifyNoInteractions(userMapper);
        }

        @Test
        @DisplayName("회원정보와 인증정보가 다르면 FAILED로 변경한다")
        void completeFailsWhenIdentityInformationDoesNotMatch() {
            IdentityDTO identity =
                    createRequestedIdentity(USER_ID);

            UserDTO user = createUser();

            PortOneIdentityResponse response =
                    createVerifiedPortOneResponse();

            DomainException mismatchException =
                    IdentityErrorCode
                            .IDENTITY_INFORMATION_MISMATCH
                            .toException();

            given(
                    identityMapper
                            .findByIdentityVerificationId(
                                    VERIFICATION_ID
                            )
            ).willReturn(identity);

            given(
                    portOneIdentityService
                            .getIdentityVerification(
                                    VERIFICATION_ID
                            )
            ).willReturn(response);

            given(userMapper.findById(USER_ID))
                    .willReturn(Optional.of(user));

            doThrow(mismatchException)
                    .when(identityValidator)
                    .validateSameUser(
                            user,
                            response.verifiedCustomer()
                    );

            given(
                    identityMapper.updateFailed(
                            VERIFICATION_ID,
                            "IDENTITY_INFORMATION_MISMATCH"
                    )
            ).willReturn(1);

            assertThatThrownBy(
                    () -> identityService.complete(
                            USER_ID,
                            VERIFICATION_ID
                    )
            ).isSameAs(mismatchException);

            verify(identityMapper)
                    .updateFailed(
                            VERIFICATION_ID,
                            "IDENTITY_INFORMATION_MISMATCH"
                    );

            verify(identityMapper, never())
                    .updateVerified(
                            any(),
                            any(),
                            any()
                    );
        }

        @Test
        @DisplayName("JWT의 userId에 해당하는 회원이 없으면 예외가 발생한다")
        void completeFailsWhenUserDoesNotExist() {
            IdentityDTO identity =
                    createRequestedIdentity(USER_ID);

            PortOneIdentityResponse response =
                    createVerifiedPortOneResponse();

            given(
                    identityMapper
                            .findByIdentityVerificationId(
                                    VERIFICATION_ID
                            )
            ).willReturn(identity);

            given(
                    portOneIdentityService
                            .getIdentityVerification(
                                    VERIFICATION_ID
                            )
            ).willReturn(response);

            given(userMapper.findById(USER_ID))
                    .willReturn(Optional.empty());

            assertThatThrownBy(
                    () -> identityService.complete(
                            USER_ID,
                            VERIFICATION_ID
                    )
            ).isInstanceOfSatisfying(
                    DomainException.class,
                    exception -> assertThat(
                            exception.getErrorCode()
                    ).isEqualTo(
                            UserErrorCode.USER_NOT_FOUND
                    )
            );

            verify(identityValidator, never())
                    .validateSameUser(
                            any(),
                            any()
                    );

            verify(identityMapper, never())
                    .updateVerified(
                            any(),
                            any(),
                            any()
                    );
        }
    }

    @Nested
    @DisplayName("본인인증 사용 처리")
    class Consume {

        @Test
        @DisplayName("사용 가능한 인증 건을 USED 상태로 변경한다")
        void consumeSuccess() {
            given(
                    identityMapper.consume(
                            VERIFICATION_ID,
                            USER_ID,
                            IdentityPurpose.LOAN_CONTRACT
                    )
            ).willReturn(1);

            identityService.consume(
                    USER_ID,
                    VERIFICATION_ID,
                    IdentityPurpose.LOAN_CONTRACT
            );

            verify(identityMapper)
                    .consume(
                            VERIFICATION_ID,
                            USER_ID,
                            IdentityPurpose.LOAN_CONTRACT
                    );
        }

        @Test
        @DisplayName("인증 건을 사용할 수 없으면 예외가 발생한다")
        void consumeFailsWhenVerificationIsUnavailable() {
            given(
                    identityMapper.consume(
                            VERIFICATION_ID,
                            USER_ID,
                            IdentityPurpose.LOAN_CONTRACT
                    )
            ).willReturn(0);

            assertIdentityError(
                    () -> identityService.consume(
                            USER_ID,
                            VERIFICATION_ID,
                            IdentityPurpose.LOAN_CONTRACT
                    ),
                    IdentityErrorCode
                            .IDENTITY_VERIFICATION_CONSUME_FAILED
            );
        }
    }

    private IdentityDTO createRequestedIdentity(
            Long ownerUserId
    ) {
        return IdentityDTO.builder()
                .identityVerificationId(
                        VERIFICATION_ID
                )
                .userId(ownerUserId)
                .purpose(
                        IdentityPurpose.LOAN_CONTRACT
                )
                .status(
                        IdentityStatus.REQUESTED
                )
                .requestedAt(
                        LocalDateTime.now()
                )
                .build();
    }

    private UserDTO createUser() {
        return UserDTO.builder()
                .userId(USER_ID)
                .userToken("SAI-ABCDEFGH")
                .userKey(null)
                .email("user@example.com")
                .password("encoded-password")
                .name(USER_NAME)
                .birthDate(BIRTH_DATE)
                .build();
    }

    private PortOneIdentityResponse
    createVerifiedPortOneResponse() {
        return new PortOneIdentityResponse(
                VERIFICATION_ID,
                "VERIFIED",
                new PortOneIdentityResponse.VerifiedCustomer(
                        USER_NAME,
                        BIRTH_DATE,
                        "ci-test"
                ),
                null
        );
    }

    private void assertIdentityError(
            Runnable operation,
            IdentityErrorCode expectedErrorCode
    ) {
        assertThatThrownBy(operation::run)
                .isInstanceOfSatisfying(
                        DomainException.class,
                        exception -> assertThat(
                                exception.getErrorCode()
                        ).isEqualTo(expectedErrorCode)
                );
    }
}
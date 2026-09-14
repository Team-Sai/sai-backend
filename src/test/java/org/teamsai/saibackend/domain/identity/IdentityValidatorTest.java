package org.teamsai.saibackend.domain.identity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.teamsai.saibackend.domain.identity.dto.response.PortOneIdentityResponse;
import org.teamsai.saibackend.domain.identity.exception.IdentityErrorCode;
import org.teamsai.saibackend.domain.identity.service.IdentityValidator;
import org.teamsai.saibackend.domain.user.dto.UserDTO;
import org.teamsai.saibackend.global.exception.DomainException;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("IdentityValidator 단위 테스트")
class IdentityValidatorTest {

    private static final String VERIFICATION_ID =
            "identity-verification-test1234";

    private static final LocalDate BIRTH_DATE =
            LocalDate.of(2002, 10, 22);

    private final IdentityValidator identityValidator =
            new IdentityValidator();

    @Nested
    @DisplayName("포트원 응답 검증")
    class ValidatePortOneResponse {

        @Test
        @DisplayName("요청 ID와 상태가 정상적이면 검증을 통과한다")
        void validResponse() {
            PortOneIdentityResponse response =
                    new PortOneIdentityResponse(
                            VERIFICATION_ID,
                            "VERIFIED",
                            null,
                            null
                    );

            assertThatCode(
                    () -> identityValidator
                            .validatePortOneResponse(
                                    VERIFICATION_ID,
                                    response
                            )
            ).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("포트원 응답이 null이면 예외가 발생한다")
        void nullResponse() {
            assertIdentityError(
                    () -> identityValidator
                            .validatePortOneResponse(
                                    VERIFICATION_ID,
                                    null
                            ),
                    IdentityErrorCode
                            .PORTONE_API_INVALID_RESPONSE
            );
        }

        @Test
        @DisplayName("포트원 응답의 인증 ID가 다르면 예외가 발생한다")
        void differentVerificationId() {
            PortOneIdentityResponse response =
                    new PortOneIdentityResponse(
                            "different-id",
                            "VERIFIED",
                            null,
                            null
                    );

            assertIdentityError(
                    () -> identityValidator
                            .validatePortOneResponse(
                                    VERIFICATION_ID,
                                    response
                            ),
                    IdentityErrorCode
                            .PORTONE_API_INVALID_RESPONSE
            );
        }

        @Test
        @DisplayName("알 수 없는 상태값이면 예외가 발생한다")
        void unknownStatus() {
            PortOneIdentityResponse response =
                    new PortOneIdentityResponse(
                            VERIFICATION_ID,
                            "UNKNOWN",
                            null,
                            null
                    );

            assertIdentityError(
                    () -> identityValidator
                            .validatePortOneResponse(
                                    VERIFICATION_ID,
                                    response
                            ),
                    IdentityErrorCode
                            .PORTONE_API_INVALID_RESPONSE
            );
        }
    }

    @Nested
    @DisplayName("동일인 검증")
    class ValidateSameUser {

        @Test
        @DisplayName("이름과 생년월일이 같으면 검증을 통과한다")
        void sameUser() {
            UserDTO user = createUser(
                    " 김사이 ",
                    BIRTH_DATE
            );

            PortOneIdentityResponse.VerifiedCustomer customer =
                    createCustomer(
                            "김사이",
                            BIRTH_DATE
                    );

            assertThatCode(
                    () -> identityValidator
                            .validateSameUser(
                                    user,
                                    customer
                            )
            ).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("이름이 다르면 동일인 검증에 실패한다")
        void differentName() {
            UserDTO user = createUser(
                    "김사이",
                    BIRTH_DATE
            );

            PortOneIdentityResponse.VerifiedCustomer customer =
                    createCustomer(
                            "박사이",
                            BIRTH_DATE
                    );

            assertIdentityError(
                    () -> identityValidator
                            .validateSameUser(
                                    user,
                                    customer
                            ),
                    IdentityErrorCode
                            .IDENTITY_INFORMATION_MISMATCH
            );
        }

        @Test
        @DisplayName("생년월일이 다르면 동일인 검증에 실패한다")
        void differentBirthDate() {
            UserDTO user = createUser(
                    "김사이",
                    BIRTH_DATE
            );

            PortOneIdentityResponse.VerifiedCustomer customer =
                    createCustomer(
                            "김사이",
                            LocalDate.of(2001, 10, 22)
                    );

            assertIdentityError(
                    () -> identityValidator
                            .validateSameUser(
                                    user,
                                    customer
                            ),
                    IdentityErrorCode
                            .IDENTITY_INFORMATION_MISMATCH
            );
        }

        @Test
        @DisplayName("회원의 이름 또는 생년월일이 없으면 예외가 발생한다")
        void missingUserInformation() {
            UserDTO user = createUser(
                    null,
                    BIRTH_DATE
            );

            PortOneIdentityResponse.VerifiedCustomer customer =
                    createCustomer(
                            "김사이",
                            BIRTH_DATE
                    );

            assertIdentityError(
                    () -> identityValidator
                            .validateSameUser(
                                    user,
                                    customer
                            ),
                    IdentityErrorCode
                            .IDENTITY_USER_INFORMATION_MISSING
            );
        }

        @Test
        @DisplayName("포트원 인증자 정보가 없으면 잘못된 응답으로 처리한다")
        void missingVerifiedCustomer() {
            UserDTO user = createUser(
                    "김사이",
                    BIRTH_DATE
            );

            assertIdentityError(
                    () -> identityValidator
                            .validateSameUser(
                                    user,
                                    null
                            ),
                    IdentityErrorCode
                            .PORTONE_API_INVALID_RESPONSE
            );
        }
    }

    private UserDTO createUser(
            String name,
            LocalDate birthDate
    ) {
        return UserDTO.builder()
                .userId(2L)
                .name(name)
                .birthDate(birthDate)
                .build();
    }

    private PortOneIdentityResponse.VerifiedCustomer
    createCustomer(
            String name,
            LocalDate birthDate
    ) {
        return new PortOneIdentityResponse.VerifiedCustomer(
                name,
                birthDate,
                "ci-test"
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
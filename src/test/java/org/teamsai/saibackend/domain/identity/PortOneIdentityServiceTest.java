package org.teamsai.saibackend.domain.identity;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.teamsai.saibackend.domain.identity.dto.response.PortOneIdentityResponse;
import org.teamsai.saibackend.domain.identity.exception.IdentityErrorCode;
import org.teamsai.saibackend.domain.identity.service.PortOneIdentityService;
import org.teamsai.saibackend.global.exception.DomainException;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("PortOneIdentityService 단위 테스트")
class PortOneIdentityServiceTest {

    private static final String VERIFICATION_ID =
            "identity-verification-test1234";

    private static final String API_SECRET =
            "test-api-secret";

    private HttpServer httpServer;
    private PortOneIdentityService portOneIdentityService;

    @BeforeEach
    void setUp() throws IOException {
        httpServer = HttpServer.create(
                new InetSocketAddress(0),
                0
        );

        String baseUrl =
                "http://localhost:"
                        + httpServer.getAddress().getPort();

        portOneIdentityService =
                new PortOneIdentityService(
                        baseUrl,
                        API_SECRET
                );
    }

    @AfterEach
    void tearDown() {
        if (httpServer != null) {
            httpServer.stop(0);
        }
    }

    @Test
    @DisplayName("Authorization 헤더를 전송하고 인증 결과를 조회한다")
    void getIdentityVerificationSuccess() {
        httpServer.createContext(
                "/identity-verifications/"
                        + VERIFICATION_ID,
                exchange -> {
                    String authorization =
                            exchange
                                    .getRequestHeaders()
                                    .getFirst("Authorization");

                    if (!("PortOne " + API_SECRET)
                            .equals(authorization)) {

                        respond(
                                exchange,
                                401,
                                """
                                {
                                  "type": "UNAUTHORIZED"
                                }
                                """
                        );

                        return;
                    }

                    respond(
                            exchange,
                            200,
                            """
                            {
                              "id": "%s",
                              "status": "VERIFIED",
                              "verifiedCustomer": {
                                "name": "김사이",
                                "birthDate": "2002-10-22",
                                "ci": "ci-test"
                              }
                            }
                            """.formatted(VERIFICATION_ID)
                    );
                }
        );

        httpServer.start();

        PortOneIdentityResponse result =
                portOneIdentityService
                        .getIdentityVerification(
                                VERIFICATION_ID
                        );

        assertThat(result.id())
                .isEqualTo(VERIFICATION_ID);

        assertThat(result.status())
                .isEqualTo("VERIFIED");

        assertThat(result.verifiedCustomer().name())
                .isEqualTo("김사이");
    }

    @Test
    @DisplayName("포트원 서버가 401을 반환하면 API 호출 실패 예외가 발생한다")
    void getIdentityVerificationFailsWhenUnauthorized() {
        httpServer.createContext(
                "/identity-verifications/"
                        + VERIFICATION_ID,
                exchange -> respond(
                        exchange,
                        401,
                        """
                        {
                          "type": "UNAUTHORIZED"
                        }
                        """
                )
        );

        httpServer.start();

        assertThatThrownBy(
                () -> portOneIdentityService
                        .getIdentityVerification(
                                VERIFICATION_ID
                        )
        ).isInstanceOfSatisfying(
                DomainException.class,
                exception -> assertThat(
                        exception.getErrorCode()
                ).isEqualTo(
                        IdentityErrorCode
                                .PORTONE_API_CALL_FAILED
                )
        );
    }

    @Test
    @DisplayName("포트원 서버 응답 본문이 없으면 잘못된 응답 예외가 발생한다")
    void getIdentityVerificationFailsWhenBodyIsEmpty() {
        httpServer.createContext(
                "/identity-verifications/"
                        + VERIFICATION_ID,
                exchange -> respond(
                        exchange,
                        200,
                        ""
                )
        );

        httpServer.start();

        assertThatThrownBy(
                () -> portOneIdentityService
                        .getIdentityVerification(
                                VERIFICATION_ID
                        )
        ).isInstanceOfSatisfying(
                DomainException.class,
                exception -> assertThat(
                        exception.getErrorCode()
                ).isEqualTo(
                        IdentityErrorCode
                                .PORTONE_API_INVALID_RESPONSE
                )
        );
    }

    private void respond(
            HttpExchange exchange,
            int status,
            String body
    ) throws IOException {
        byte[] responseBody =
                body.getBytes(StandardCharsets.UTF_8);

        exchange.getResponseHeaders().set(
                "Content-Type",
                "application/json; charset=UTF-8"
        );

        exchange.sendResponseHeaders(
                status,
                responseBody.length
        );

        exchange.getResponseBody()
                .write(responseBody);

        exchange.close();
    }
}
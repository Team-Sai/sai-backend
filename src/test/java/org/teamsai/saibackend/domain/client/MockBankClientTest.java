package org.teamsai.saibackend.domain.client;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.teamsai.saibackend.domain.account.dto.response.AccountDetailResponse;
import org.teamsai.saibackend.domain.account.dto.response.LinkableAccountResponse;
import org.teamsai.saibackend.global.client.MockBankClient;
import org.teamsai.saibackend.global.exception.DomainException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

@DisplayName("MockBankClient 단위 테스트")
class MockBankClientTest {

    private MockBankClient mockBankClient;
    private MockRestServiceServer mockServer;

    private static final String BASE_URL = "http://localhost:8081";
    private static final String API_KEY = "test-internal-api-key";

    @BeforeEach
    void setUp() {
        mockBankClient = new MockBankClient(BASE_URL, API_KEY);

        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        mockServer = MockRestServiceServer.bindTo(builder).build();
        RestClient boundRestClient = builder.build();

        ReflectionTestUtils.setField(mockBankClient, "restClient", boundRestClient);
    }

    @Test
    @DisplayName("requestUserKey - 성공 시 userKey를 반환한다")
    void requestUserKey_성공() {
        mockServer.expect(requestTo(BASE_URL + "/api/mock-bank/link"))
                .andExpect(method(org.springframework.http.HttpMethod.POST))
                .andRespond(withSuccess(
                        "{\"userKey\":\"mb_abc123\",\"issuedAt\":\"2026-08-16T10:00:00\"}",
                        MediaType.APPLICATION_JSON
                ));

        String userKey = mockBankClient.requestUserKey("홍길동", "token-abc");

        assertThat(userKey).isEqualTo("mb_abc123");
        mockServer.verify();
    }

    @Test
    @DisplayName("requestUserKey - 응답 바디가 없으면 BANK_SERVER_UNAVAILABLE 예외가 발생한다")
    void requestUserKey_바디없으면_예외() {
        mockServer.expect(requestTo(BASE_URL + "/api/mock-bank/link"))
                .andRespond(withSuccess().contentType(MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> mockBankClient.requestUserKey("홍길동", "token-abc"))
                .isInstanceOf(DomainException.class);

        mockServer.verify();
    }

    @Test
    @DisplayName("confirmUserKey - 성공하면 예외 없이 완료된다")
    void confirmUserKey_성공() {
        mockServer.expect(requestTo(BASE_URL + "/api/link/confirm-key"))
                .andExpect(method(org.springframework.http.HttpMethod.POST))
                .andExpect(header("X-Internal-Api-Key", API_KEY))
                .andExpect(jsonPath("$.userKey").value("mb_rawkey"))
                .andRespond(withSuccess());

        mockBankClient.confirmUserKey("mb_rawkey");

        mockServer.verify();
    }

    @Test
    @DisplayName("confirmUserKey - mock-bank가 실패 응답을 주면 예외가 그대로 전파된다")
    void confirmUserKey_실패시_예외전파() {
        mockServer.expect(requestTo(BASE_URL + "/api/link/confirm-key"))
                .andRespond(withStatus(HttpStatus.CONFLICT));

        assertThatThrownBy(() -> mockBankClient.confirmUserKey("mb_rawkey"))
                .isInstanceOf(Exception.class);

        mockServer.verify();
    }

    @Test
    @DisplayName("confirmUserKey - 네트워크 오류(5xx)도 예외로 전파된다")
    void confirmUserKey_서버오류시_예외전파() {
        mockServer.expect(requestTo(BASE_URL + "/api/link/confirm-key"))
                .andRespond(withServerError());

        assertThatThrownBy(() -> mockBankClient.confirmUserKey("mb_rawkey"))
                .isInstanceOf(Exception.class);

        mockServer.verify();
    }

    @Test
    @DisplayName("revokeUserKey - 성공하면 예외 없이 완료된다")
    void revokeUserKey_성공() {
        mockServer.expect(requestTo(BASE_URL + "/api/link/revoke-key"))
                .andExpect(method(org.springframework.http.HttpMethod.POST))
                .andExpect(header("X-Internal-Api-Key", API_KEY))
                .andExpect(jsonPath("$.userKey").value("mb_rawkey"))
                .andRespond(withSuccess());

        mockBankClient.revokeUserKey("mb_rawkey");

        mockServer.verify();
    }

    @Test
    @DisplayName("revokeUserKey - mock-bank가 실패 응답을 주면 예외가 그대로 전파된다")
    void revokeUserKey_실패시_예외전파() {
        mockServer.expect(requestTo(BASE_URL + "/api/link/revoke-key"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThatThrownBy(() -> mockBankClient.revokeUserKey("mb_rawkey"))
                .isInstanceOf(Exception.class);

        mockServer.verify();
    }

    @Test
    @DisplayName("revokeUserKey - 네트워크 오류(5xx)도 예외로 전파된다")
    void revokeUserKey_서버오류시_예외전파() {
        mockServer.expect(requestTo(BASE_URL + "/api/link/revoke-key"))
                .andRespond(withServerError());

        assertThatThrownBy(() -> mockBankClient.revokeUserKey("mb_rawkey"))
                .isInstanceOf(Exception.class);

        mockServer.verify();
    }

    @Test
    @DisplayName("getAccountsByUserKey - X-User-Key 헤더를 포함해 요청한다")
    void getAccountsByUserKey_헤더포함() {
        mockServer.expect(requestTo(BASE_URL + "/api/mock-bank/accounts"))
                .andExpect(header("X-User-Key", "mb_rawkey"))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        List<LinkableAccountResponse> result = mockBankClient.getAccountsByUserKey("mb_rawkey");

        assertThat(result).isEmpty();
        mockServer.verify();
    }

    @Test
    @DisplayName("getAccountDetail - accountId를 경로에 포함해 요청한다")
    void getAccountDetail_경로변수포함() {
        mockServer.expect(requestTo(BASE_URL + "/api/mock-bank/accounts/1"))
                .andExpect(header("X-User-Key", "mb_rawkey"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThatThrownBy(() -> mockBankClient.getAccountDetail(1L, "mb_rawkey"))
                .isInstanceOf(Exception.class);

        mockServer.verify();
    }

    @Test
    @DisplayName("getTransactions - afterTransactionId 쿼리파라미터를 포함해 요청한다")
    void getTransactions_쿼리파라미터포함() {
        mockServer.expect(requestTo(BASE_URL + "/api/mock-bank/accounts/1/transactions?afterTransactionId=100"))
                .andExpect(header("X-User-Key", "mb_rawkey"))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        var result = mockBankClient.getTransactions(1L, "mb_rawkey", 100L);

        assertThat(result).isEmpty();
        mockServer.verify();
    }
}
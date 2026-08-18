package org.teamsai.saibackend.global.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.teamsai.saibackend.domain.account.dto.response.AccountDetailResponse;
import org.teamsai.saibackend.domain.account.dto.response.LinkableAccountResponse;
import org.teamsai.saibackend.domain.account.exception.AccountErrorCode;
import org.teamsai.saibackend.domain.transaction.dto.response.BankTransactionResponse;

import java.util.List;

@Slf4j
@Component
public class MockBankClient {

    private static final String USER_KEY_HEADER = "X-User-Key";

    private final RestClient restClient;
    private final String internalApiKey;

    public MockBankClient(@Value("${sai.mock-bank.base-url}") String baseUrl, @Value("${link-callback.api-key}") String internalApiKey) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(3000);
        requestFactory.setReadTimeout(5000);

        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .build();
        this.internalApiKey = internalApiKey;
    }

    private <T> T requireBody(T body) {
        if (body == null) {
            throw AccountErrorCode.BANK_SERVER_UNAVAILABLE.toException();
        }
        return body;
    }

    public String requestUserKey(String name, String userToken) {
        MockBankLinkRequest request = new MockBankLinkRequest(name, userToken);

        MockBankLinkResponse response = restClient.post()
                .uri("/api/mock-bank/link")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(MockBankLinkResponse.class);

        return requireBody(response).userKey();
    }

    public void confirmUserKey(String userKey) {
        restClient.post()
                .uri("/api/link/confirm-key")
                .header("X-Internal-Api-Key", internalApiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new UserKeyRequest(userKey))
                .retrieve()
                .toBodilessEntity();
    }

    public void revokeUserKey(String userKey) {
        restClient.post()
                .uri("/api/link/revoke-key")
                .header("X-Internal-Api-Key", internalApiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new UserKeyRequest(userKey))
                .retrieve()
                .toBodilessEntity();
    }

    public void restoreUserKey(String currentUserKey, String previousUserKey){
        restClient.post()
                .uri("/api/link/restore-key")
                .header("X-Internal-Api-Key", internalApiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new RestoreKeyRequest(currentUserKey, previousUserKey))
                .retrieve()
                .toBodilessEntity();
    }

    private record UserKeyRequest(String userKey) {}

    private record RestoreKeyRequest(String currentUserKey, String previousUserKey) {}

    public List<LinkableAccountResponse> getAccountsByUserKey(String userKey) {
        return requireBody(
                restClient.get()
                        .uri("/api/mock-bank/accounts")
                        .header(USER_KEY_HEADER, userKey)
                        .retrieve()
                        .body(new ParameterizedTypeReference<List<LinkableAccountResponse>>() {})
        );
    }

    public AccountDetailResponse getAccountDetail(Long accountId, String userKey) {
        return requireBody(
                restClient.get()
                        .uri("/api/mock-bank/accounts/{accountId}", accountId)
                        .header(USER_KEY_HEADER, userKey)
                        .retrieve()
                        .body(AccountDetailResponse.class)
        );
    }

    public List<BankTransactionResponse> getTransactions(Long accountId, String userKey, Long afterTransactionId) {
        List<BankTransactionResponse> response = restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/mock-bank/accounts/{accountId}/transactions")
                        .queryParam("afterTransactionId", afterTransactionId)
                        .build(accountId))
                .header(USER_KEY_HEADER, userKey)
                .retrieve()
                .body(new ParameterizedTypeReference<List<BankTransactionResponse>>() {});

        return requireBody(response);
    }

    private record MockBankLinkRequest(String name, String userToken) {}
    private record MockBankLinkResponse(String userKey, String issuedAt) {}
}
package org.teamsai.saibackend.global.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.teamsai.saibackend.domain.account.dto.AccountDetailResponse;
import org.teamsai.saibackend.domain.account.dto.LinkableAccountResponse;

import java.util.List;
import java.util.Objects;

@Slf4j
@Component
public class MockBankClient {

    private final RestClient restClient;

    public MockBankClient(@Value("${mock-bank.base-url:http://localhost:8081}") String baseUrl) {
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .build();
    }

    public String requestUserKey(String name, String userToken) {
        //log.info("[MockBankClient] 연동키 발급 요청 -> name: {}", name);

        MockBankLinkRequest request = new MockBankLinkRequest(name, userToken);

        MockBankLinkResponse response = restClient.post()
                .uri("/api/mock-bank/link")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(MockBankLinkResponse.class);

        return Objects.requireNonNull(response).userKey();
    }

    public List<LinkableAccountResponse> getAccountsByUserKey(String userKey) {
        //log.info("[MockBankClient] 계좌 목록 조회 요청 -> userKey: {}", userKey);

        return restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/mock-bank/accounts")
                        .queryParam("userKey", userKey)
                        .build())
                .retrieve()
                .body(new ParameterizedTypeReference<List<LinkableAccountResponse>>() {});
    }

    public AccountDetailResponse getAccountDetail(Long accountId, String userKey) {
        return restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/mock-bank/accounts/{accountId}")
                        .queryParam("userKey", userKey)
                        .build(accountId))
                .retrieve()
                .body(AccountDetailResponse.class);
    }

    private record MockBankLinkRequest(String name, String userToken) {}
    private record MockBankLinkResponse(String userKey, String issuedAt) {}
}

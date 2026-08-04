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

import java.util.List;

@Slf4j
@Component
public class MockBankClient {

    private final RestClient restClient;

    public MockBankClient(@Value("${mock-bank.base-url}") String baseUrl) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(3000);
        requestFactory.setReadTimeout(5000);

        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .build();
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

    public List<LinkableAccountResponse> getAccountsByUserKey(String userKey) {
        return requireBody(
                restClient.get()
                        .uri(uriBuilder -> uriBuilder
                                .path("/api/mock-bank/accounts")
                                .queryParam("userKey", userKey)
                                .build())
                        .retrieve()
                        .body(new ParameterizedTypeReference<List<LinkableAccountResponse>>() {})
        );
    }

    public AccountDetailResponse getAccountDetail(Long accountId, String userKey) {
        return requireBody(
                restClient.get()
                        .uri(uriBuilder -> uriBuilder
                                .path("/api/mock-bank/accounts/{accountId}")
                                .queryParam("userKey", userKey)
                                .build(accountId))
                        .retrieve()
                        .body(AccountDetailResponse.class)
        );
    }

    private record MockBankLinkRequest(String name, String userToken) {}
    private record MockBankLinkResponse(String userKey, String issuedAt) {}
}

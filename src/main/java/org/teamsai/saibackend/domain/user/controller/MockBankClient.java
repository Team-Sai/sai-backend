package org.teamsai.saibackend.domain.user.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Objects;

@Component
public class MockBankClient {

    private final RestClient restClient;

    public MockBankClient(@Value("${mock-bank.base-url}") String baseUrl) {
        this.restClient = RestClient.builder().baseUrl(baseUrl).build();
    }

    public String requestUserKey(String name, String userToken) {
        MockBankLinkRequest request = new MockBankLinkRequest(name, userToken);

        MockBankLinkResponse response = restClient.post()
                .uri("/api/mock-bank/link")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(MockBankLinkResponse.class);

        return Objects.requireNonNull(response).userKey();
    }

    private record MockBankLinkRequest(String name, String userToken) {}
    private record MockBankLinkResponse(String userKey, String issuedAt) {}
}

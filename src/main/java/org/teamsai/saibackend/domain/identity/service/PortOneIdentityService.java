package org.teamsai.saibackend.domain.identity.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.teamsai.saibackend.domain.identity.dto.response.PortOneIdentityResponse;
import org.teamsai.saibackend.domain.identity.exception.IdentityErrorCode;

@Slf4j
@Service
public class PortOneIdentityService {

    private final RestClient restClient;
    private final String apiSecret;

    public PortOneIdentityService(
            @Value("${portone.identity.base-url:https://api.portone.io}")
            String apiBaseUrl,

            @Value("${portone.identity.api-secret}")
            String apiSecret
    ) {
        if (!StringUtils.hasText(apiSecret)) {
            throw new IllegalStateException(
                    "포트원 V2 API Secret이 설정되지 않았습니다."
            );
        }

        this.restClient = RestClient.builder()
                .baseUrl(apiBaseUrl)
                .build();

        this.apiSecret = apiSecret;
    }

    public PortOneIdentityResponse getIdentityVerification(
            String identityVerificationId
    ) {
        try {
            PortOneIdentityResponse response =
                    restClient.get()
                            .uri(
                                    "/identity-verifications/{identityVerificationId}",
                                    identityVerificationId
                            )
                            .header(
                                    HttpHeaders.AUTHORIZATION,
                                    "PortOne " + apiSecret
                            )
                            .retrieve()
                            .body(PortOneIdentityResponse.class);

            if (response == null) {
                throw IdentityErrorCode
                        .PORTONE_API_INVALID_RESPONSE
                        .toException();
            }

            return response;

        } catch (RestClientResponseException exception) {
            log.warn(
                    "포트원 본인인증 조회 실패: id={}, status={}, body={}",
                    identityVerificationId,
                    exception.getStatusCode(),
                    exception.getResponseBodyAsString()
            );

            throw IdentityErrorCode
                    .PORTONE_API_CALL_FAILED
                    .toException();

        } catch (RestClientException exception) {
            log.warn(
                    "포트원 본인인증 통신 오류: id={}, type={}, message={}",
                    identityVerificationId,
                    exception.getClass().getSimpleName(),
                    exception.getMessage(),
                    exception
            );

            throw IdentityErrorCode
                    .PORTONE_API_CALL_FAILED
                    .toException();
        }
    }
}
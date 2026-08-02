package org.teamsai.saibackend.domain.identity.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.teamsai.saibackend.domain.identity.dto.IdentityDTO;
import org.teamsai.saibackend.domain.identity.dto.request.IdentityPrepareRequest;
import org.teamsai.saibackend.domain.identity.dto.response.IdentityCompleteResponse;
import org.teamsai.saibackend.domain.identity.dto.response.IdentityPrepareResponse;
import org.teamsai.saibackend.domain.identity.dto.response.PortOneIdentityResponse;
import org.teamsai.saibackend.domain.identity.exception.IdentityErrorCode;
import org.teamsai.saibackend.domain.identity.mapper.IdentityMapper;
import org.teamsai.saibackend.domain.identity.type.IdentityPurpose;
import org.teamsai.saibackend.domain.identity.type.IdentityStatus;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

@Service
public class IdentityService {

    private static final String PORTONE_API_BASE_URL =
            "https://api.portone.io";

    private static final String PORTONE_STATUS_VERIFIED =
            "VERIFIED";

    private static final String PORTONE_STATUS_FAILED =
            "FAILED";

    private static final String IDENTITY_VERIFICATION_ID_PREFIX =
            "identity-verification-";

    private final IdentityMapper identityMapper;
    private final RestClient portOneRestClient;

    private final String storeId;
    private final String channelKey;
    private final String apiSecret;
    private final long validMinutes;

    public IdentityService(
            IdentityMapper identityMapper,

            @Value("${portone.store-id}")
            String storeId,

            @Value("${portone.channel-key}")
            String channelKey,

            @Value("${portone.api-secret}")
            String apiSecret,

            @Value("${portone.identity-valid-minutes:10}")
            long validMinutes
    ) {
        this.identityMapper = identityMapper;
        this.storeId = storeId;
        this.channelKey = channelKey;
        this.apiSecret = apiSecret;
        this.validMinutes = validMinutes;

        this.portOneRestClient = RestClient.builder()
                .baseUrl(PORTONE_API_BASE_URL)
                .build();
    }

    /**
     * 본인인증 요청 준비
     *
     * 1. 포트원 인증 식별값 생성
     * 2. REQUESTED 상태로 DB 저장
     * 3. 프론트엔드 SDK 호출에 필요한 값 반환
     */
    @Transactional
    public IdentityPrepareResponse prepare(
            Long userId,
            IdentityPrepareRequest request
    ) {
        validateUserId(userId);
        validatePrepareRequest(request);

        String identityVerificationId =
                generateIdentityVerificationId();

        LocalDateTime requestedAt =
                LocalDateTime.now();

        IdentityDTO identity = IdentityDTO.builder()
                .identityVerificationId(identityVerificationId)
                .userId(userId)
                .purpose(request.purpose())
                .status(IdentityStatus.REQUESTED)
                .requestedAt(requestedAt)
                .build();

        int insertedCount =
                identityMapper.insert(identity);

        if (insertedCount != 1) {
            throw IdentityErrorCode
                    .IDENTITY_VERIFICATION_CREATE_FAILED
                    .toException();
        }

        return new IdentityPrepareResponse(
                identityVerificationId,
                storeId,
                channelKey
        );
    }

    /**
     * 본인인증 완료 처리
     *
     * 프론트엔드의 인증 성공 응답을 그대로 신뢰하지 않고,
     * 포트원 서버에서 인증 결과를 다시 조회한다.
     */
    public IdentityCompleteResponse complete(
            Long userId,
            String identityVerificationId
    ) {
        validateUserId(userId);
        validateIdentityVerificationId(identityVerificationId);

        IdentityDTO identity =
                findIdentity(identityVerificationId);

        validateOwner(identity, userId);

        if (identity.getStatus() == IdentityStatus.VERIFIED) {
            return toCompleteResponse(identity);
        }

        if (identity.getStatus() == IdentityStatus.FAILED) {
            throw IdentityErrorCode
                    .PORTONE_VERIFICATION_NOT_VERIFIED
                    .toException();
        }

        if (identity.getStatus() != IdentityStatus.REQUESTED) {
            throw IdentityErrorCode
                    .INVALID_IDENTITY_VERIFICATION_STATUS
                    .toException();
        }

        PortOneIdentityResponse portOneResponse =
                requestPortOneVerification(identityVerificationId);

        String portOneStatus =
                portOneResponse.status();

        if (PORTONE_STATUS_FAILED.equals(portOneStatus)) {
            processFailure(identityVerificationId);

            throw IdentityErrorCode
                    .PORTONE_VERIFICATION_NOT_VERIFIED
                    .toException();
        }

        /*
         * READY 등 아직 인증 완료 상태가 아닌 경우
         * 로컬 상태를 FAILED로 변경하지 않는다.
         */
        if (!PORTONE_STATUS_VERIFIED.equals(portOneStatus)) {
            throw IdentityErrorCode
                    .PORTONE_VERIFICATION_NOT_VERIFIED
                    .toException();
        }

        LocalDateTime verifiedAt =
                LocalDateTime.now();

        LocalDateTime expiresAt =
                verifiedAt.plusMinutes(validMinutes);

        int updatedCount =
                identityMapper.updateVerified(
                        identityVerificationId,
                        verifiedAt,
                        expiresAt
                );

        if (updatedCount != 1) {
            return handleConcurrentCompletion(
                    userId,
                    identityVerificationId
            );
        }

        return new IdentityCompleteResponse(
                identityVerificationId,
                IdentityStatus.VERIFIED,
                verifiedAt,
                expiresAt
        );
    }

    /**
     * 완료된 본인인증 건을 특정 기능에서 1회 사용 처리한다.
     *
     * 차용증 확정 등 본인인증이 필요한 실제 기능에서 호출한다.
     */
    @Transactional
    public void consume(
            Long userId,
            String identityVerificationId,
            IdentityPurpose purpose
    ) {
        validateUserId(userId);
        validateIdentityVerificationId(identityVerificationId);

        if (purpose == null) {
            throw IdentityErrorCode
                    .INVALID_IDENTITY_PURPOSE
                    .toException();
        }

        int updatedCount =
                identityMapper.consume(
                        identityVerificationId,
                        userId,
                        purpose
                );

        if (updatedCount != 1) {
            throw IdentityErrorCode
                    .IDENTITY_VERIFICATION_CONSUME_FAILED
                    .toException();
        }
    }

    /**
     * 포트원 본인인증 단건 조회 API 호출
     */
    private PortOneIdentityResponse requestPortOneVerification(
            String identityVerificationId
    ) {
        try {
            PortOneIdentityResponse response =
                    portOneRestClient.get()
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

            if (response == null
                    || response.status() == null
                    || response.status().isBlank()) {

                throw IdentityErrorCode
                        .PORTONE_API_INVALID_RESPONSE
                        .toException();
            }

            return response;

        } catch (RestClientException exception) {
            throw IdentityErrorCode
                    .PORTONE_API_CALL_FAILED
                    .toException();
        }
    }

    private IdentityDTO findIdentity(
            String identityVerificationId
    ) {
        IdentityDTO identity =
                identityMapper.findByIdentityVerificationId(
                        identityVerificationId
                );

        if (identity == null) {
            throw IdentityErrorCode
                    .IDENTITY_VERIFICATION_NOT_FOUND
                    .toException();
        }

        return identity;
    }

    private void validateOwner(
            IdentityDTO identity,
            Long userId
    ) {
        if (!Objects.equals(identity.getUserId(), userId)) {
            throw IdentityErrorCode
                    .IDENTITY_VERIFICATION_FORBIDDEN
                    .toException();
        }
    }

    private void processFailure(
            String identityVerificationId
    ) {
        identityMapper.updateFailed(
                identityVerificationId,
                PORTONE_STATUS_FAILED
        );
    }

    /**
     * 완료 요청이 동시에 들어온 경우 최신 DB 상태를 다시 확인한다.
     */
    private IdentityCompleteResponse handleConcurrentCompletion(
            Long userId,
            String identityVerificationId
    ) {
        IdentityDTO latestIdentity =
                findIdentity(identityVerificationId);

        validateOwner(latestIdentity, userId);

        if (latestIdentity.getStatus()
                == IdentityStatus.VERIFIED) {

            return toCompleteResponse(latestIdentity);
        }

        throw IdentityErrorCode
                .IDENTITY_VERIFICATION_UPDATE_FAILED
                .toException();
    }

    private IdentityCompleteResponse toCompleteResponse(
            IdentityDTO identity
    ) {
        return new IdentityCompleteResponse(
                identity.getIdentityVerificationId(),
                identity.getStatus(),
                identity.getVerifiedAt(),
                identity.getExpiresAt()
        );
    }

    private String generateIdentityVerificationId() {
        return IDENTITY_VERIFICATION_ID_PREFIX
                + UUID.randomUUID()
                .toString()
                .replace("-", "");
    }

    private void validateUserId(Long userId) {
        if (userId == null) {
            throw IdentityErrorCode
                    .UNAUTHENTICATED_USER
                    .toException();
        }
    }

    private void validatePrepareRequest(
            IdentityPrepareRequest request
    ) {
        if (request == null || request.purpose() == null) {
            throw IdentityErrorCode
                    .INVALID_IDENTITY_PURPOSE
                    .toException();
        }
    }

    private void validateIdentityVerificationId(
            String identityVerificationId
    ) {
        if (identityVerificationId == null
                || identityVerificationId.isBlank()) {

            throw IdentityErrorCode
                    .INVALID_IDENTITY_VERIFICATION_ID
                    .toException();
        }
    }
}
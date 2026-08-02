package org.teamsai.saibackend.domain.identity.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.teamsai.saibackend.domain.identity.dto.request.IdentityPrepareRequest;
import org.teamsai.saibackend.domain.identity.dto.response.IdentityCompleteResponse;
import org.teamsai.saibackend.domain.identity.dto.response.IdentityPrepareResponse;
import org.teamsai.saibackend.domain.identity.service.IdentityService;

@RestController
@RequestMapping("/api/identity-verifications")
@RequiredArgsConstructor
public class IdentityController {

    private final IdentityService identityService;

    @PostMapping
    public ResponseEntity<IdentityPrepareResponse> prepare(
            @AuthenticationPrincipal
            Long userId,

            @Valid
            @RequestBody
            IdentityPrepareRequest request
    ) {
        IdentityPrepareResponse response =
                identityService.prepare(
                        userId,
                        request
                );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    @PostMapping("/{identityVerificationId}/complete")
    public ResponseEntity<IdentityCompleteResponse> complete(
            @AuthenticationPrincipal
            Long userId,

            @PathVariable
            String identityVerificationId
    ) {
        IdentityCompleteResponse response =
                identityService.complete(
                        userId,
                        identityVerificationId
                );

        return ResponseEntity.ok(response);
    }
}
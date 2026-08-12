package org.teamsai.saibackend.domain.identity.service;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.teamsai.saibackend.domain.identity.dto.response.PortOneIdentityResponse;
import org.teamsai.saibackend.domain.identity.exception.IdentityErrorCode;
import org.teamsai.saibackend.domain.user.dto.UserDTO;

import java.text.Normalizer;
import java.util.Objects;
import java.util.Set;

@Component
public class IdentityValidator {

    private static final Set<String> PORTONE_STATUSES =
            Set.of(
                    "READY",
                    "VERIFIED",
                    "FAILED"
            );

    public void validatePortOneResponse(
            String expectedIdentityVerificationId,
            PortOneIdentityResponse response
    ) {
        if (response == null
                || !StringUtils.hasText(response.id())
                || !StringUtils.hasText(response.status())) {

            throw IdentityErrorCode
                    .PORTONE_API_INVALID_RESPONSE
                    .toException();
        }

        if (!Objects.equals(
                expectedIdentityVerificationId,
                response.id()
        )) {
            throw IdentityErrorCode
                    .PORTONE_API_INVALID_RESPONSE
                    .toException();
        }

        if (!PORTONE_STATUSES.contains(response.status())) {
            throw IdentityErrorCode
                    .PORTONE_API_INVALID_RESPONSE
                    .toException();
        }
    }

    public void validateSameUser(
            UserDTO user,
            PortOneIdentityResponse.VerifiedCustomer verifiedCustomer
    ) {
        validateUserInformation(user);
        validateVerifiedCustomer(verifiedCustomer);

        boolean sameName =
                Objects.equals(
                        normalizeName(user.getName()),
                        normalizeName(verifiedCustomer.name())
                );

        boolean sameBirthDate =
                Objects.equals(
                        user.getBirthDate(),
                        verifiedCustomer.birthDate()
                );

        if (!sameName || !sameBirthDate) {
            throw IdentityErrorCode
                    .IDENTITY_INFORMATION_MISMATCH
                    .toException();
        }
    }

    public void validateUserInformation(
            UserDTO user
    ) {
        if (user == null
                || !StringUtils.hasText(user.getName())
                || user.getBirthDate() == null) {

            throw IdentityErrorCode
                    .IDENTITY_USER_INFORMATION_MISSING
                    .toException();
        }
    }

    private void validateVerifiedCustomer(
            PortOneIdentityResponse.VerifiedCustomer verifiedCustomer
    ) {
        if (verifiedCustomer == null
                || !StringUtils.hasText(verifiedCustomer.name())
                || verifiedCustomer.birthDate() == null) {

            throw IdentityErrorCode
                    .PORTONE_API_INVALID_RESPONSE
                    .toException();
        }
    }

    private String normalizeName(
            String name
    ) {
        return Normalizer.normalize(
                name.strip(),
                Normalizer.Form.NFC
        );
    }
}
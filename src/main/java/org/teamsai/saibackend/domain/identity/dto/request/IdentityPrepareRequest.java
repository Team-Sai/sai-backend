package org.teamsai.saibackend.domain.identity.dto.request;

import lombok.NonNull;
import org.teamsai.saibackend.domain.identity.type.IdentityPurpose;

public record IdentityPrepareRequest(@NonNull IdentityPurpose purpose) {
}

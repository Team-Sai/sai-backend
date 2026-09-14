package org.teamsai.saibackend.domain.matching.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record MatchingReviewApplyRequest(
        @NotNull
        @Positive
        Long matchCandidateId
) {
}

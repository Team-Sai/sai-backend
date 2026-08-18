package org.teamsai.saibackend.domain.matching.dto.request;

import org.teamsai.saibackend.domain.matching.exception.MatchingErrorCode;
import org.teamsai.saibackend.domain.matching.type.MatchingReviewChannel;
import org.teamsai.saibackend.domain.matching.type.MatchingTargetType;

public record MatchingReviewSearchCondition(
        MatchingReviewChannel reviewChannel,
        MatchingTargetType targetType,
        Long aggregateId,
        long page,
        long size
) {

    public MatchingReviewSearchCondition {
        if (reviewChannel == null
                || aggregateId != null && (aggregateId <= 0 || targetType == null)
                || reviewChannel == MatchingReviewChannel.NOTIFICATION
                && (targetType != null || aggregateId != null)) {
            throw MatchingErrorCode.INVALID_MATCHING_REQUEST.toException();
        }

        if (page < 0) {
            page = 0;
        }
        if (size <= 0 || size > 100) {
            size = 20;
        }
    }

    public long offset() {
        return page * size;
    }

    public boolean isNotificationChannel() {
        return reviewChannel == MatchingReviewChannel.NOTIFICATION;
    }
}

package org.teamsai.saibackend.domain.matching;

import org.junit.jupiter.api.Test;
import org.teamsai.saibackend.domain.matching.dto.request.MatchingReviewSearchCondition;
import org.teamsai.saibackend.domain.matching.exception.MatchingErrorCode;
import org.teamsai.saibackend.domain.matching.type.MatchingReviewChannel;
import org.teamsai.saibackend.domain.matching.type.MatchingTargetType;
import org.teamsai.saibackend.global.exception.DomainException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MatchingReviewSearchConditionTest {

    @Test
    void allowsTransactionHistoryWithoutTargetType() {
        MatchingReviewSearchCondition condition =
                new MatchingReviewSearchCondition(
                        MatchingReviewChannel.TRANSACTION_HISTORY,
                        null,
                        null,
                        -1,
                        0
                );

        assertThat(condition.page()).isZero();
        assertThat(condition.size()).isEqualTo(20);
    }

    @Test
    void rejectsAggregateIdWithoutTargetType() {
        assertInvalid(() -> new MatchingReviewSearchCondition(
                MatchingReviewChannel.TRANSACTION_HISTORY,
                null,
                10L,
                0,
                20
        ));
    }

    @Test
    void rejectsDomainFiltersForNotificationChannel() {
        assertInvalid(() -> new MatchingReviewSearchCondition(
                MatchingReviewChannel.NOTIFICATION,
                MatchingTargetType.SETTLEMENT,
                null,
                0,
                20
        ));
    }

    private void assertInvalid(Runnable action) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(
                        DomainException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(MatchingErrorCode.INVALID_MATCHING_REQUEST)
                );
    }
}

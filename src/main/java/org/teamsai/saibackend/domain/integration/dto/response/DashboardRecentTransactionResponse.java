package org.teamsai.saibackend.domain.integration.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Builder;
import lombok.Getter;
import org.teamsai.saibackend.domain.integration.type.DashboardTransactionStatus;
import org.teamsai.saibackend.domain.payment.type.PaymentTargetType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Builder
public class DashboardRecentTransactionResponse {

    private Long targetId;

    private PaymentTargetType type;

    private String title;

    private DashboardTransactionStatus status;

    private BigDecimal amount;

    private String detailUrl;

    @JsonIgnore
    private LocalDateTime createdAt;
}

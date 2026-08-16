package org.teamsai.saibackend.domain.calendar.response;

import lombok.Builder;
import lombok.Getter;
import org.teamsai.saibackend.domain.payment.type.PaymentTargetType;

import java.math.BigDecimal;

@Getter@Builder
public class DashboardCalendarItemResponse {

    private Long targetId;
    private PaymentTargetType type;
    private String title;
    private String subLabel;
    private BigDecimal amount;
    private String detailUrl;
}

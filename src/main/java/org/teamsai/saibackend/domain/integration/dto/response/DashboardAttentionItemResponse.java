package org.teamsai.saibackend.domain.integration.dto.response;

import lombok.Builder;
import lombok.Getter;
import org.teamsai.saibackend.domain.integration.type.DashboardAttentionType;

@Getter
@Builder
public class DashboardAttentionItemResponse {

    private Long id;
    private DashboardAttentionType type;
    private Long remainingDays;
    private String actionUrl;
}

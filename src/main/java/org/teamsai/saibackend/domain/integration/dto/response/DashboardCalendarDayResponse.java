package org.teamsai.saibackend.domain.integration.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;

@Getter
@Builder
public class DashboardCalendarDayResponse {

    private LocalDate date;
    private boolean hasInbound;
    private boolean hasOutbound;
}

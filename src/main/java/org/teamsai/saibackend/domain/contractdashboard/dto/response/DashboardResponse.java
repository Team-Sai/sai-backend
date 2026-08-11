package org.teamsai.saibackend.domain.contractdashboard.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class DashboardResponse {

    private DashboardSummaryResponse summary;
    private List<DashboardContractRowResponse> contracts;
    private int currentPage;
    private int totalPages;
    private long totalCount;
    private int pageSize;
}

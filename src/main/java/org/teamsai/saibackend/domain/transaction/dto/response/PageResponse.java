package org.teamsai.saibackend.domain.transaction.dto.response;

import java.util.List;

public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalCount
) {
    public static <T> PageResponse<T> of(List<T> content, int page, int size, long totalCount) {
        return new PageResponse<>(content, page, size, totalCount);
    }
}
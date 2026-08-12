package org.teamsai.saibackend.domain.transaction.dto.response;

import java.util.List;

public record PageResponse<T>(
        List<T> content,
        long page,
        long size,
        long totalCount,
        long totalPages,
        boolean hasNext,
        boolean hasPrevious
) {
    public static <T> PageResponse<T> of(List<T> content, long page, long size, long totalCount) {
        long totalPages = size <= 0 ? 0 : (long) Math.ceil((double) totalCount / size);
        boolean hasNext = page < totalPages - 1;
        boolean hasPrevious = page > 0;

        return new PageResponse<>(content, page, size, totalCount, totalPages, hasNext, hasPrevious);
    }
}
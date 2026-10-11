package com.procureflow.shared.web;

import java.util.List;
import java.util.function.Function;

/**
 * Page envelope for list endpoints: {@code content} carries the rows,
 * {@code totalElements} the full count for paginator controls. Page indexes
 * are zero-based; {@code size} is capped so a careless (or hostile) caller
 * cannot pull unbounded result sets. This is the contract target the API
 * guide names; endpoints adopt it one vertical slice at a time.
 */
public record Paged<T>(List<T> content, int page, int size, long totalElements, int totalPages) {

    public static final int DEFAULT_SIZE = 20;
    public static final int MAX_SIZE = 100;

    public static <T> Paged<T> of(List<T> content, int page, int size, long totalElements) {
        int totalPages = size <= 0 ? 1 : (int) Math.ceil((double) totalElements / size);
        return new Paged<>(List.copyOf(content), page, size, totalElements, totalPages);
    }

    public <R> Paged<R> map(Function<T, R> mapper) {
        return new Paged<>(content.stream().map(mapper).toList(), page, size, totalElements, totalPages);
    }

    public static int pageOrThrow(Integer page) {
        if (page != null && page < 0) {
            throw ApiException.badRequest("INVALID_PAGE", "page must be >= 0");
        }
        return page == null ? 0 : page;
    }

    public static int sizeOrThrow(Integer size) {
        if (size != null && (size < 1 || size > MAX_SIZE)) {
            throw ApiException.badRequest("INVALID_PAGE", "size must be between 1 and " + MAX_SIZE);
        }
        return size == null ? DEFAULT_SIZE : size;
    }
}

package com.hugo.huds.common.response;


import org.springframework.data.domain.Page;

import java.util.List;

/**
 * A page of results returned to the client.
 *
 * @param content       the items of the requested page
 * @param page          the zero-based page index
 * @param size          the requested page size
 * @param totalElements the total number of items over all pages
 * @param totalPages    the total number of pages
 * @param <T>           the type of the items
 * @author Meiyan Wang
 * @date 2026-10-23 10:09 a.m.
 */
public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {

    public static <T> PageResponse<T> of(Page<T> page) {
        return new PageResponse<>(page.getContent(), page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages());
    }

    public static <T> PageResponse<T> of(List<T> list, int page, int size, long totalElements, int totalPages) {
        return new PageResponse<>(list, page, size,
                totalElements, totalPages);
    }
}

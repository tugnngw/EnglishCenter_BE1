package com.sba301.englishcenter.common;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PageResponse<T> {

    private int currentPage;
    private int pageSize;
    private int totalPages;
    private long totalElements;
    private List<T> data;
}

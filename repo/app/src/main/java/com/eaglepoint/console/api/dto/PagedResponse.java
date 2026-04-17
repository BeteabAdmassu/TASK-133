package com.eaglepoint.console.api.dto;

import com.eaglepoint.console.model.PagedResult;

import java.util.List;

public class PagedResponse<T> {
    private List<T> data;
    private int total;
    private int page;
    private int pageSize;
    private int totalPages;

    public static <T> PagedResponse<T> of(PagedResult<T> result) {
        PagedResponse<T> r = new PagedResponse<>();
        r.data = result.getData();
        r.total = result.getTotal();
        r.page = result.getPage();
        r.pageSize = result.getPageSize();
        r.totalPages = result.getPageSize() > 0
            ? (int) Math.ceil((double) result.getTotal() / result.getPageSize())
            : 0;
        return r;
    }

    public List<T> getData() { return data; }
    public int getTotal() { return total; }
    public int getPage() { return page; }
    public int getPageSize() { return pageSize; }
    public int getTotalPages() { return totalPages; }
}

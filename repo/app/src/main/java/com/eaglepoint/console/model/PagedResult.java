package com.eaglepoint.console.model;

import java.util.List;

public class PagedResult<T> {
    private List<T> data;
    private int total;
    private int page;
    private int pageSize;

    public PagedResult(List<T> data, int total, int page, int pageSize) {
        this.data = data;
        this.total = total;
        this.page = page;
        this.pageSize = pageSize;
    }

    public List<T> getData() { return data; }
    public void setData(List<T> data) { this.data = data; }
    public int getTotal() { return total; }
    public void setTotal(int total) { this.total = total; }
    public int getPage() { return page; }
    public void setPage(int page) { this.page = page; }
    public int getPageSize() { return pageSize; }
    public void setPageSize(int pageSize) { this.pageSize = pageSize; }
}

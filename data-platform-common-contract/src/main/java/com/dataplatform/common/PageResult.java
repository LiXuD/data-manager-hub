package com.dataplatform.common.result;

import java.io.Serializable;
import java.util.List;

/**
 * 分页结果
 */
public class PageResult<T> extends Result<List<T>> implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private Long total;
    private Integer page;
    private Integer pageSize;

    public PageResult() {
        super();
    }

    public Long getTotal() { return total; }
    public void setTotal(Long total) { this.total = total; }
    public Integer getPage() { return page; }
    public void setPage(Integer page) { this.page = page; }
    public Integer getPageSize() { return pageSize; }
    public void setPageSize(Integer pageSize) { this.pageSize = pageSize; }

    public static <T> PageResult<T> of(List<T> data, Long total, Integer page, Integer pageSize) {
        PageResult<T> result = new PageResult<>();
        result.setCode(0);
        result.setMessage("success");
        result.setData(data);
        result.setTotal(total);
        result.setPage(page);
        result.setPageSize(pageSize);
        return result;
    }
}
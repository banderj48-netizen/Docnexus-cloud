package com.xyf.docnexus.common.VO;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 通用分页响应对象。
 *
 * <p>所有需要分页展示的接口都可以复用该对象，前端只需要读取 records、total、pageNum、pageSize、pages。</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PageResponse<T> {

    /**
     * 当前页数据。
     */
    private List<T> records;

    /**
     * 总记录数。
     */
    private Long total;

    /**
     * 当前页码，从 1 开始。
     */
    private Integer pageNum;

    /**
     * 每页条数。
     */
    private Integer pageSize;

    /**
     * 总页数。
     */
    private Long pages;

    public static <T> PageResponse<T> of(List<T> records, Long total, Integer pageNum, Integer pageSize) {
        long safeTotal = total == null ? 0L : total;
        int safePageNum = pageNum == null || pageNum < 1 ? 1 : pageNum;
        int safePageSize = pageSize == null || pageSize < 1 ? 10 : pageSize;
        long pages = safeTotal == 0 ? 0 : (safeTotal + safePageSize - 1) / safePageSize;
        return new PageResponse<>(records, safeTotal, safePageNum, safePageSize, pages);
    }
}

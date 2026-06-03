package com.xyf.docnexuslogservice.mapper;

import com.xyf.docnexuslogservice.entity.SecurityAlertLog;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * 安全告警日志 Mapper。
 */
public interface SecurityAlertLogMapper {

    /**
     * 幂等插入安全告警日志。
     */
    int insertIgnore(SecurityAlertLog log);

    /**
     * 分页查询安全告警日志。
     */
    List<SecurityAlertLog> selectPage(@Param("alertType") String alertType,
                                      @Param("handled") Integer handled,
                                      @Param("offset") int offset,
                                      @Param("pageSize") int pageSize);

    /**
     * 统计安全告警日志数量。
     */
    long count(@Param("alertType") String alertType,
               @Param("handled") Integer handled);

    /**
     * 标记安全告警为已处理。
     */
    int markHandled(@Param("eventId") String eventId);

    /**
     * 按告警类型分组统计，用于前端饼图。
     */
    List<Map<String, Object>> countByAlertType();
}

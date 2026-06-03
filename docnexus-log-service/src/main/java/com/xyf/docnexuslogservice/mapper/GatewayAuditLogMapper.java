package com.xyf.docnexuslogservice.mapper;

import com.xyf.docnexuslogservice.entity.GatewayAuditLog;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * 网关审计日志 Mapper。
 */
public interface GatewayAuditLogMapper {

    /**
     * 幂等插入网关审计日志。
     */
    int insertIgnore(GatewayAuditLog log);

    /**
     * 分页查询网关审计日志。
     */
    List<GatewayAuditLog> selectPage(@Param("userId") Long userId,
                                     @Param("path") String path,
                                     @Param("statusCode") Integer statusCode,
                                     @Param("offset") int offset,
                                     @Param("pageSize") int pageSize);

    /**
     * 统计网关审计日志数量。
     */
    long count(@Param("userId") Long userId,
               @Param("path") String path,
               @Param("statusCode") Integer statusCode);

    /**
     * 按响应结果分组统计，用于前端饼图。
     */
    List<Map<String, Object>> countByStatusGroup();
}

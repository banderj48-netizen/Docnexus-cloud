package com.xyf.docnexuslogservice.mapper;

import com.xyf.docnexuslogservice.entity.BusinessOperationLog;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 业务操作耗时日志 Mapper。
 */
public interface BusinessOperationLogMapper {

    /**
     * 幂等写入业务操作日志。
     */
    int insertIgnore(BusinessOperationLog log);

    /**
     * 分页查询当前用户最近 5 天内可见的主动业务操作日志。
     */
    List<BusinessOperationLog> selectUserVisiblePage(@Param("userId") Long userId,
                                                     @Param("success") Boolean success,
                                                     @Param("module") String module,
                                                     @Param("functionName") String functionName,
                                                     @Param("since") LocalDateTime since,
                                                     @Param("offset") int offset,
                                                     @Param("pageSize") int pageSize);

    /**
     * 统计当前用户最近 5 天内可见的主动业务操作日志数量。
     */
    long countUserVisible(@Param("userId") Long userId,
                          @Param("success") Boolean success,
                          @Param("module") String module,
                          @Param("functionName") String functionName,
                          @Param("since") LocalDateTime since);

    /**
     * 按成功失败统计当前用户最近 5 天内可见日志。
     */
    List<Map<String, Object>> countUserVisibleBySuccess(@Param("userId") Long userId,
                                                        @Param("since") LocalDateTime since);

    /**
     * 按业务功能统计当前用户最近 5 天内可见日志。
     */
    List<Map<String, Object>> countUserVisibleByFunction(@Param("userId") Long userId,
                                                         @Param("since") LocalDateTime since);
}

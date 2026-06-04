package com.xyf.docnexus.file.mapper;

import com.xyf.docnexus.file.entity.DocumentProcessTask;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 文档处理任务 Mapper。
 */
@Mapper
public interface DocumentProcessTaskMapper {

    /**
     * 创建等待解析任务。
     */
    @Insert("""
            INSERT INTO document_process_task (
                task_id, file_id, user_id, task_type, task_status,
                stage, progress, created_at, updated_at
            ) VALUES (
                #{taskId}, #{fileId}, #{userId}, #{taskType}, #{taskStatus},
                #{stage}, #{progress}, NOW(), NOW()
            )
            """)
    int insert(DocumentProcessTask task);

    /**
     * 根据任务 ID 回写解析任务状态。
     */
    @Update("""
            UPDATE document_process_task
            SET task_status = #{taskStatus},
                stage = #{stage},
                progress = #{progress},
                updated_at = NOW()
            WHERE task_id = #{taskId}
              AND user_id = #{userId}
              AND file_id = #{fileId}
            """)
    int updateTaskStatus(@Param("taskId") String taskId,
                         @Param("userId") Long userId,
                         @Param("fileId") String fileId,
                         @Param("taskStatus") String taskStatus,
                         @Param("stage") String stage,
                         @Param("progress") Integer progress);
}

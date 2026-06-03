package com.xyf.docnexus.file.mapper;

import com.xyf.docnexus.file.entity.DocumentProcessTask;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;

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
}

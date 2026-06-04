package com.xyf.docnexus.file.mapper;

import com.xyf.docnexus.file.entity.DocumentContentRevisionLog;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;

/**
 * 文档保存覆盖日志 Mapper。
 */
@Mapper
public interface DocumentContentRevisionLogMapper {

    /**
     * 插入文档保存覆盖日志。
     */
    @Insert("""
            INSERT INTO document_content_revision_log (
                event_id, file_id, user_id, old_version, new_version,
                old_object_key, new_object_key, content_hash, created_at
            ) VALUES (
                #{eventId}, #{fileId}, #{userId}, #{oldVersion}, #{newVersion},
                #{oldObjectKey}, #{newObjectKey}, #{contentHash}, NOW()
            )
            """)
    int insert(DocumentContentRevisionLog log);
}

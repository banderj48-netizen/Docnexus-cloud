package com.xyf.docnexus.file.mapper;

import com.xyf.docnexus.file.entity.DocumentFileContent;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 文档在线查看内容快照 Mapper。
 */
@Mapper
public interface DocumentFileContentMapper {

    /**
     * 查询指定文件版本的内容快照。
     */
    @Select("""
            SELECT *
            FROM document_file_content
            WHERE user_id = #{userId}
              AND file_id = #{fileId}
              AND version_number = #{versionNumber}
            LIMIT 1
            """)
    DocumentFileContent selectByFileAndVersion(@Param("userId") Long userId,
                                               @Param("fileId") String fileId,
                                               @Param("versionNumber") Integer versionNumber);

    /**
     * 插入或更新当前版本内容快照。
     */
    @Insert("""
            INSERT INTO document_file_content (
                file_id, user_id, version_number, content_format, content_html,
                plain_text, content_hash, source_bucket, source_object_key,
                created_at, updated_at
            ) VALUES (
                #{fileId}, #{userId}, #{versionNumber}, #{contentFormat}, #{contentHtml},
                #{plainText}, #{contentHash}, #{sourceBucket}, #{sourceObjectKey},
                NOW(), NOW()
            )
            ON DUPLICATE KEY UPDATE
                content_format = VALUES(content_format),
                content_html = VALUES(content_html),
                plain_text = VALUES(plain_text),
                content_hash = VALUES(content_hash),
                source_bucket = VALUES(source_bucket),
                source_object_key = VALUES(source_object_key),
                updated_at = NOW()
            """)
    int upsert(DocumentFileContent content);
}

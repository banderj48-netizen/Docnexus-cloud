package com.xyf.docnexus.file.mapper;

import com.xyf.docnexus.file.entity.DocumentFile;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 文档文件元数据 Mapper。
 */
@Mapper
public interface DocumentFileMapper {

    /**
     * 插入上传成功后的正式文件元数据。
     */
    @Insert("""
            INSERT INTO document_file (
                file_id, user_id, knowledge_base_id, original_name, file_category,
                file_ext, mime_type, file_size, file_sha256, storage_type,
                bucket_name, object_key, upload_status, parse_status, index_status,
                graph_status, deleted, created_at, updated_at
            ) VALUES (
                #{fileId}, #{userId}, #{knowledgeBaseId}, #{originalName}, #{fileCategory},
                #{fileExt}, #{mimeType}, #{fileSize}, #{fileSha256}, #{storageType},
                #{bucketName}, #{objectKey}, #{uploadStatus}, #{parseStatus}, #{indexStatus},
                #{graphStatus}, #{deleted}, NOW(), NOW()
            )
            """)
    int insert(DocumentFile file);

    /**
     * 分页查询当前用户已上传文档。
     */
    @Select("""
            SELECT *
            FROM document_file
            WHERE user_id = #{userId}
              AND knowledge_base_id = #{knowledgeBaseId}
              AND deleted = 0
            ORDER BY created_at DESC
            LIMIT #{offset}, #{pageSize}
            """)
    List<DocumentFile> selectPage(@Param("userId") Long userId,
                                  @Param("knowledgeBaseId") String knowledgeBaseId,
                                  @Param("offset") int offset,
                                  @Param("pageSize") int pageSize);

    /**
     * 统计当前用户已上传文档数量。
     */
    @Select("""
            SELECT COUNT(1)
            FROM document_file
            WHERE user_id = #{userId}
              AND knowledge_base_id = #{knowledgeBaseId}
              AND deleted = 0
            """)
    long countByUser(@Param("userId") Long userId, @Param("knowledgeBaseId") String knowledgeBaseId);

    /**
     * 查询当前用户单个文件。
     */
    @Select("""
            SELECT *
            FROM document_file
            WHERE user_id = #{userId}
              AND file_id = #{fileId}
              AND deleted = 0
            LIMIT 1
            """)
    DocumentFile selectByUserAndFileId(@Param("userId") Long userId, @Param("fileId") String fileId);

    /**
     * 软删除当前用户文件。
     */
    @Update("""
            UPDATE document_file
            SET upload_status = 'DELETED',
                deleted = 1,
                updated_at = NOW()
            WHERE user_id = #{userId}
              AND file_id = #{fileId}
              AND deleted = 0
            """)
    int softDelete(@Param("userId") Long userId, @Param("fileId") String fileId);
}

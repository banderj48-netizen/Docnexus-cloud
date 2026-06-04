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
                graph_status, parse_retry_count, current_version, editable, content_hash, deleted, created_at, updated_at
            ) VALUES (
                #{fileId}, #{userId}, #{knowledgeBaseId}, #{originalName}, #{fileCategory},
                #{fileExt}, #{mimeType}, #{fileSize}, #{fileSha256}, #{storageType},
                #{bucketName}, #{objectKey}, #{uploadStatus}, #{parseStatus}, #{indexStatus},
                #{graphStatus}, #{parseRetryCount}, #{currentVersion}, #{editable}, #{contentHash}, #{deleted}, NOW(), NOW()
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

    /**
     * 乐观锁更新当前文件对象和解析状态。
     */
    @Update("""
            UPDATE document_file
            SET bucket_name = #{bucketName},
                object_key = #{objectKey},
                file_size = #{fileSize},
                file_sha256 = #{fileSha256},
                content_hash = #{contentHash},
                current_version = current_version + 1,
                parse_status = 'PENDING',
                index_status = 'NONE',
                graph_status = 'NONE',
                summary = NULL,
                keywords_json = NULL,
                error_message = NULL,
                parse_retry_count = 0,
                last_saved_at = NOW(),
                updated_at = NOW()
            WHERE user_id = #{userId}
              AND file_id = #{fileId}
              AND current_version = #{currentVersion}
              AND deleted = 0
            """)
    int updateEditorObjectByVersion(@Param("userId") Long userId,
                                    @Param("fileId") String fileId,
                                    @Param("currentVersion") Integer currentVersion,
                                    @Param("bucketName") String bucketName,
                                    @Param("objectKey") String objectKey,
                                    @Param("fileSize") Long fileSize,
                                    @Param("fileSha256") String fileSha256,
                                    @Param("contentHash") String contentHash);

    /**
     * 更新文件编辑能力和内容 hash。
     */
    @Update("""
            UPDATE document_file
            SET editable = #{editable},
                content_hash = #{contentHash},
                updated_at = NOW()
            WHERE user_id = #{userId}
              AND file_id = #{fileId}
              AND deleted = 0
            """)
    int updateEditorSnapshot(@Param("userId") Long userId,
                             @Param("fileId") String fileId,
                             @Param("editable") Integer editable,
                             @Param("contentHash") String contentHash);

    /**
     * 用户手动提交解析请求，更新解析状态和重新解析次数。
     */
    @Update("""
            UPDATE document_file
            SET parse_status = 'PENDING',
                index_status = 'NONE',
                graph_status = 'NONE',
                summary = NULL,
                keywords_json = NULL,
                error_message = NULL,
                parse_retry_count = #{parseRetryCount},
                updated_at = NOW()
            WHERE user_id = #{userId}
              AND file_id = #{fileId}
              AND parse_status = #{expectedParseStatus}
              AND deleted = 0
            """)
    int markParseRequested(@Param("userId") Long userId,
                           @Param("fileId") String fileId,
                           @Param("expectedParseStatus") String expectedParseStatus,
                           @Param("parseRetryCount") Integer parseRetryCount);

    /**
     * MQ 消费者领取任务后标记文件正在解析。
     */
    @Update("""
            UPDATE document_file
            SET parse_status = 'PROCESSING',
                updated_at = NOW()
            WHERE user_id = #{userId}
              AND file_id = #{fileId}
              AND parse_status = 'PENDING'
              AND deleted = 0
            """)
    int markParseProcessing(@Param("userId") Long userId, @Param("fileId") String fileId);

    /**
     * 解析服务回调解析结果。
     */
    @Update("""
            UPDATE document_file
            SET parse_status = #{parseStatus},
                index_status = #{indexStatus},
                graph_status = #{graphStatus},
                summary = #{summary},
                keywords_json = #{keywordsJson},
                error_message = #{errorMessage},
                updated_at = NOW()
            WHERE user_id = #{userId}
              AND file_id = #{fileId}
              AND deleted = 0
            """)
    int updateParseResult(@Param("userId") Long userId,
                          @Param("fileId") String fileId,
                          @Param("parseStatus") String parseStatus,
                          @Param("indexStatus") String indexStatus,
                          @Param("graphStatus") String graphStatus,
                          @Param("summary") String summary,
                          @Param("keywordsJson") String keywordsJson,
                          @Param("errorMessage") String errorMessage);
}

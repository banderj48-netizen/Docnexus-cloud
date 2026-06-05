package com.xyf.docnexus.file.mapper;

import com.xyf.docnexus.file.entity.FileUploadSession;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 文件上传会话 Mapper。
 */
@Mapper
public interface FileUploadSessionMapper {

    /**
     * 创建上传会话。
     */
    @Insert("""
            INSERT INTO file_upload_session (
                upload_id, user_id, file_id, knowledge_base_id, file_name, display_name,
                file_size, file_category, file_ext, mime_type, file_sha256,
                knowledge_space_code, knowledge_space_name, business_category_code, business_category_name,
                document_type, document_tags_json, metadata_draft_json,
                chunk_size, total_chunks, uploaded_chunks, status, bucket_name,
                object_key, temp_bucket_name, temp_prefix, error_message, expires_at,
                created_at, updated_at
            ) VALUES (
                #{uploadId}, #{userId}, #{fileId}, #{knowledgeBaseId}, #{fileName}, #{displayName},
                #{fileSize}, #{fileCategory}, #{fileExt}, #{mimeType}, #{fileSha256},
                #{knowledgeSpaceCode}, #{knowledgeSpaceName}, #{businessCategoryCode}, #{businessCategoryName},
                #{documentType}, #{documentTagsJson}, #{metadataDraftJson},
                #{chunkSize}, #{totalChunks}, #{uploadedChunks}, #{status}, #{bucketName},
                #{objectKey}, #{tempBucketName}, #{tempPrefix}, #{errorMessage}, #{expiresAt},
                NOW(), NOW()
            )
            """)
    int insert(FileUploadSession session);

    /**
     * 按用户和上传 ID 查询会话。
     */
    @Select("""
            SELECT *
            FROM file_upload_session
            WHERE user_id = #{userId}
              AND upload_id = #{uploadId}
            LIMIT 1
            """)
    FileUploadSession selectByUserAndUploadId(@Param("userId") Long userId, @Param("uploadId") String uploadId);

    /**
     * 标记上传中并更新分片数量。
     */
    @Update("""
            UPDATE file_upload_session
            SET status = #{status},
                uploaded_chunks = #{uploadedChunks},
                error_message = #{errorMessage},
                updated_at = NOW()
            WHERE upload_id = #{uploadId}
            """)
    int updateStatusAndChunks(FileUploadSession session);

    /**
     * 标记上传完成。
     */
    @Update("""
            UPDATE file_upload_session
            SET status = 'UPLOADED',
                file_id = #{fileId},
                bucket_name = #{bucketName},
                object_key = #{objectKey},
                uploaded_chunks = #{uploadedChunks},
                error_message = NULL,
                updated_at = NOW()
            WHERE upload_id = #{uploadId}
            """)
    int markUploaded(FileUploadSession session);

    /**
     * 标记上传失败。
     */
    @Update("""
            UPDATE file_upload_session
            SET status = 'UPLOAD_FAILED',
                error_message = #{errorMessage},
                updated_at = NOW()
            WHERE upload_id = #{uploadId}
            """)
    int markFailed(@Param("uploadId") String uploadId, @Param("errorMessage") String errorMessage);

    /**
     * 取消上传会话。
     */
    @Update("""
            UPDATE file_upload_session
            SET status = 'CANCELED',
                error_message = NULL,
                updated_at = NOW()
            WHERE user_id = #{userId}
              AND upload_id = #{uploadId}
            """)
    int markCanceled(@Param("userId") Long userId, @Param("uploadId") String uploadId);

    /**
     * 查询用户失败上传会话。
     */
    @Select("""
            SELECT *
            FROM file_upload_session
            WHERE user_id = #{userId}
              AND status = 'UPLOAD_FAILED'
            ORDER BY created_at ASC
            """)
    List<FileUploadSession> selectFailedByUser(Long userId);

    /**
     * 查询用户可恢复上传会话。
     */
    @Select("""
            SELECT *
            FROM file_upload_session
            WHERE user_id = #{userId}
              AND status IN ('PENDING_UPLOAD', 'UPLOADING', 'COMPLETING', 'INTERRUPTED', 'UPLOAD_FAILED')
            ORDER BY updated_at DESC
            """)
    List<FileUploadSession> selectRecoverableByUser(Long userId);

    /**
     * 查询用户需要中断标记的上传会话。
     */
    @Select("""
            SELECT *
            FROM file_upload_session
            WHERE user_id = #{userId}
              AND status IN ('PENDING_UPLOAD', 'UPLOADING', 'COMPLETING')
            ORDER BY updated_at DESC
            """)
    List<FileUploadSession> selectInterruptibleByUser(Long userId);

    /**
     * 标记上传会话为可恢复中断。
     */
    @Update("""
            UPDATE file_upload_session
            SET status = 'INTERRUPTED',
                error_message = '上传已中断，可重新选择文件继续上传',
                updated_at = NOW()
            WHERE user_id = #{userId}
              AND upload_id = #{uploadId}
              AND status IN ('PENDING_UPLOAD', 'UPLOADING', 'COMPLETING')
            """)
    int markInterrupted(@Param("userId") Long userId, @Param("uploadId") String uploadId);

    /**
     * 查询过期的未完成上传会话。
     */
    @Select("""
            SELECT *
            FROM file_upload_session
            WHERE status IN ('PENDING_UPLOAD', 'UPLOADING', 'INTERRUPTED', 'UPLOAD_FAILED')
              AND expires_at < #{now}
            ORDER BY expires_at ASC
            LIMIT #{limit}
            """)
    List<FileUploadSession> selectExpiredSessions(@Param("now") LocalDateTime now, @Param("limit") int limit);

    /**
     * 标记上传会话已过期。
     */
    @Update("""
            UPDATE file_upload_session
            SET status = 'EXPIRED',
                error_message = '上传会话已过期，临时文件已清理',
                updated_at = NOW()
            WHERE upload_id = #{uploadId}
              AND status IN ('PENDING_UPLOAD', 'UPLOADING', 'INTERRUPTED', 'UPLOAD_FAILED')
            """)
    int markExpired(@Param("uploadId") String uploadId);
}

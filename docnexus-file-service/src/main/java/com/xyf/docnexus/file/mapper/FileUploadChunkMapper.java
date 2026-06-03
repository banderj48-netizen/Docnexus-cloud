package com.xyf.docnexus.file.mapper;

import com.xyf.docnexus.file.entity.FileUploadChunk;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 文件上传分片 Mapper。
 */
@Mapper
public interface FileUploadChunkMapper {

    /**
     * 插入或覆盖分片记录。
     */
    @Insert("""
            INSERT INTO file_upload_chunk (
                upload_id, chunk_index, chunk_size, chunk_sha256,
                bucket_name, object_key, status, created_at
            ) VALUES (
                #{uploadId}, #{chunkIndex}, #{chunkSize}, #{chunkSha256},
                #{bucketName}, #{objectKey}, #{status}, NOW()
            )
            ON DUPLICATE KEY UPDATE
                chunk_size = VALUES(chunk_size),
                chunk_sha256 = VALUES(chunk_sha256),
                bucket_name = VALUES(bucket_name),
                object_key = VALUES(object_key),
                status = VALUES(status),
                created_at = NOW()
            """)
    int upsert(FileUploadChunk chunk);

    /**
     * 查询已上传分片下标。
     */
    @Select("""
            SELECT chunk_index
            FROM file_upload_chunk
            WHERE upload_id = #{uploadId}
              AND status = 'UPLOADED'
            ORDER BY chunk_index ASC
            """)
    List<Integer> findUploadedIndexes(String uploadId);

    /**
     * 查询已上传分片记录。
     */
    @Select("""
            SELECT *
            FROM file_upload_chunk
            WHERE upload_id = #{uploadId}
              AND status = 'UPLOADED'
            ORDER BY chunk_index ASC
            """)
    List<FileUploadChunk> findUploadedChunks(String uploadId);

    /**
     * 统计上传成功分片数。
     */
    @Select("""
            SELECT COUNT(1)
            FROM file_upload_chunk
            WHERE upload_id = #{uploadId}
              AND status = 'UPLOADED'
            """)
    int countUploaded(String uploadId);

    /**
     * 删除会话分片记录。
     */
    @Delete("DELETE FROM file_upload_chunk WHERE upload_id = #{uploadId}")
    int deleteByUploadId(@Param("uploadId") String uploadId);
}

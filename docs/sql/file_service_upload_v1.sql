CREATE DATABASE IF NOT EXISTS `docnexus_cloud`
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_unicode_ci;

USE `docnexus_cloud`;

CREATE TABLE IF NOT EXISTS `document_file` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '数据库主键 ID',
  `file_id` VARCHAR(64) NOT NULL COMMENT '业务文件 ID',
  `user_id` BIGINT NOT NULL COMMENT '所属用户 ID',
  `knowledge_base_id` VARCHAR(64) NOT NULL DEFAULT 'default' COMMENT '知识库 ID',
  `original_name` VARCHAR(255) NOT NULL COMMENT '原始文件名',
  `file_category` VARCHAR(32) NOT NULL COMMENT '文件大类：PDF / WORD / PPT / EXCEL / TXT / IMAGE / UNKNOWN',
  `file_ext` VARCHAR(32) NOT NULL COMMENT '文件扩展名',
  `mime_type` VARCHAR(128) DEFAULT NULL COMMENT 'MIME 类型',
  `file_size` BIGINT NOT NULL COMMENT '文件大小，单位字节',
  `file_sha256` VARCHAR(64) DEFAULT NULL COMMENT '文件 SHA-256',
  `storage_type` VARCHAR(32) NOT NULL DEFAULT 'MINIO' COMMENT '存储类型',
  `bucket_name` VARCHAR(128) NOT NULL COMMENT 'MinIO bucket',
  `object_key` VARCHAR(512) NOT NULL COMMENT 'MinIO object key',
  `upload_status` VARCHAR(32) NOT NULL DEFAULT 'UPLOADED' COMMENT '上传状态：UPLOADED / DELETED',
  `parse_status` VARCHAR(32) NOT NULL DEFAULT 'PENDING' COMMENT '解析状态：PENDING / PROCESSING / SUCCESS / FAILED',
  `index_status` VARCHAR(32) NOT NULL DEFAULT 'NONE' COMMENT '索引状态：NONE / PENDING / PROCESSING / SUCCESS / FAILED',
  `graph_status` VARCHAR(32) NOT NULL DEFAULT 'NONE' COMMENT '图谱状态：NONE / PENDING / BUILDING / SUCCESS / FAILED',
  `summary` TEXT COMMENT 'AI 摘要',
  `keywords_json` TEXT COMMENT '关键词 JSON',
  `error_message` VARCHAR(1024) DEFAULT NULL COMMENT '失败原因',
  `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '软删除标记',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_document_file_file_id` (`file_id`),
  KEY `idx_document_file_user_list` (`user_id`, `knowledge_base_id`, `deleted`, `created_at` DESC),
  KEY `idx_document_file_user_parse` (`user_id`, `parse_status`, `updated_at` DESC),
  KEY `idx_document_file_user_hash` (`user_id`, `file_sha256`, `file_size`),
  KEY `idx_document_file_object` (`bucket_name`, `object_key`(255))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='文档文件元数据表';

CREATE TABLE IF NOT EXISTS `file_upload_session` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '数据库主键 ID',
  `upload_id` VARCHAR(64) NOT NULL COMMENT '上传会话 ID',
  `user_id` BIGINT NOT NULL COMMENT '所属用户 ID',
  `file_id` VARCHAR(64) DEFAULT NULL COMMENT '成功后的正式文件 ID',
  `knowledge_base_id` VARCHAR(64) NOT NULL DEFAULT 'default' COMMENT '知识库 ID',
  `file_name` VARCHAR(255) NOT NULL COMMENT '原始文件名',
  `file_size` BIGINT NOT NULL COMMENT '文件大小',
  `file_category` VARCHAR(32) NOT NULL COMMENT '文件大类',
  `file_ext` VARCHAR(32) NOT NULL COMMENT '扩展名',
  `mime_type` VARCHAR(128) DEFAULT NULL COMMENT 'MIME 类型',
  `file_sha256` VARCHAR(64) DEFAULT NULL COMMENT '文件 SHA-256',
  `chunk_size` BIGINT NOT NULL DEFAULT 10485760 COMMENT '分片大小',
  `total_chunks` INT NOT NULL DEFAULT 1 COMMENT '总分片数',
  `uploaded_chunks` INT NOT NULL DEFAULT 0 COMMENT '已上传分片数',
  `status` VARCHAR(32) NOT NULL DEFAULT 'PENDING_UPLOAD' COMMENT 'PENDING_UPLOAD / UPLOADING / COMPLETING / INTERRUPTED / UPLOADED / UPLOAD_FAILED / CANCELED / EXPIRED',
  `bucket_name` VARCHAR(128) DEFAULT NULL COMMENT '目标 bucket',
  `object_key` VARCHAR(512) DEFAULT NULL COMMENT '目标 object key',
  `temp_bucket_name` VARCHAR(128) DEFAULT NULL COMMENT '临时分片 bucket',
  `temp_prefix` VARCHAR(512) DEFAULT NULL COMMENT '临时分片前缀',
  `error_message` VARCHAR(1024) DEFAULT NULL COMMENT '失败原因',
  `expires_at` DATETIME NOT NULL COMMENT '临时会话过期清理时间',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_file_upload_session_upload_id` (`upload_id`),
  KEY `idx_file_upload_session_user_status` (`user_id`, `status`, `created_at` DESC),
  KEY `idx_file_upload_session_expire` (`status`, `expires_at`),
  KEY `idx_file_upload_session_user_file` (`user_id`, `file_name`, `file_size`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='文件上传会话表';

CREATE TABLE IF NOT EXISTS `file_upload_chunk` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '数据库主键 ID',
  `upload_id` VARCHAR(64) NOT NULL COMMENT '上传会话 ID',
  `chunk_index` INT NOT NULL COMMENT '分片序号，从 0 开始',
  `chunk_size` BIGINT NOT NULL COMMENT '分片大小',
  `chunk_sha256` VARCHAR(64) DEFAULT NULL COMMENT '分片 SHA-256',
  `bucket_name` VARCHAR(128) NOT NULL COMMENT '临时 bucket',
  `object_key` VARCHAR(512) NOT NULL COMMENT '临时 object key',
  `status` VARCHAR(32) NOT NULL DEFAULT 'UPLOADED' COMMENT 'UPLOADED / FAILED',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_file_upload_chunk_upload_index` (`upload_id`, `chunk_index`),
  KEY `idx_file_upload_chunk_upload_id` (`upload_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='文件上传分片表';

CREATE TABLE IF NOT EXISTS `document_process_task` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '数据库主键 ID',
  `task_id` VARCHAR(64) NOT NULL COMMENT '任务 ID',
  `file_id` VARCHAR(64) NOT NULL COMMENT '文件 ID',
  `user_id` BIGINT NOT NULL COMMENT '所属用户 ID',
  `task_type` VARCHAR(64) NOT NULL DEFAULT 'PARSE_DOCUMENT' COMMENT '任务类型',
  `task_status` VARCHAR(32) NOT NULL DEFAULT 'WAITING' COMMENT 'WAITING / RUNNING / SUCCESS / FAILED / CANCELED',
  `stage` VARCHAR(128) NOT NULL DEFAULT '等待解析' COMMENT '前端展示阶段',
  `progress` INT NOT NULL DEFAULT 0 COMMENT '进度 0-100',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_document_process_task_task_id` (`task_id`),
  KEY `idx_document_process_task_file` (`file_id`),
  KEY `idx_document_process_task_user_status` (`user_id`, `task_status`, `created_at` DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='文档处理任务表';

-- =========================================================
-- 兼容已有旧表的增量迁移
-- =========================================================

DROP PROCEDURE IF EXISTS add_file_column_if_missing;
DELIMITER //
CREATE PROCEDURE add_file_column_if_missing(
  IN tableName VARCHAR(64),
  IN columnName VARCHAR(64),
  IN alterSql TEXT
)
BEGIN
  IF NOT EXISTS (
    SELECT 1
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = tableName
      AND COLUMN_NAME = columnName
  ) THEN
    SET @ddl = alterSql;
    PREPARE stmt FROM @ddl;
    EXECUTE stmt;
    DEALLOCATE PREPARE stmt;
  END IF;
END//
DELIMITER ;

CALL add_file_column_if_missing('document_file', 'parse_status',
  'ALTER TABLE document_file ADD COLUMN parse_status VARCHAR(32) NOT NULL DEFAULT ''PENDING'' COMMENT ''解析状态：PENDING / PROCESSING / SUCCESS / FAILED'' AFTER upload_status');
CALL add_file_column_if_missing('document_file', 'index_status',
  'ALTER TABLE document_file ADD COLUMN index_status VARCHAR(32) NOT NULL DEFAULT ''NONE'' COMMENT ''索引状态：NONE / PENDING / PROCESSING / SUCCESS / FAILED'' AFTER parse_status');
CALL add_file_column_if_missing('document_file', 'graph_status',
  'ALTER TABLE document_file ADD COLUMN graph_status VARCHAR(32) NOT NULL DEFAULT ''NONE'' COMMENT ''图谱状态：NONE / PENDING / BUILDING / SUCCESS / FAILED'' AFTER index_status');
CALL add_file_column_if_missing('document_file', 'mime_type',
  'ALTER TABLE document_file ADD COLUMN mime_type VARCHAR(128) DEFAULT NULL COMMENT ''MIME 类型'' AFTER file_ext');
CALL add_file_column_if_missing('document_file', 'file_sha256',
  'ALTER TABLE document_file ADD COLUMN file_sha256 VARCHAR(64) DEFAULT NULL COMMENT ''文件 SHA-256'' AFTER file_size');
CALL add_file_column_if_missing('file_upload_session', 'mime_type',
  'ALTER TABLE file_upload_session ADD COLUMN mime_type VARCHAR(128) DEFAULT NULL COMMENT ''MIME 类型'' AFTER file_ext');
CALL add_file_column_if_missing('file_upload_session', 'file_sha256',
  'ALTER TABLE file_upload_session ADD COLUMN file_sha256 VARCHAR(64) DEFAULT NULL COMMENT ''文件 SHA-256'' AFTER mime_type');
CALL add_file_column_if_missing('file_upload_session', 'chunk_size',
  'ALTER TABLE file_upload_session ADD COLUMN chunk_size BIGINT NOT NULL DEFAULT 10485760 COMMENT ''分片大小'' AFTER file_sha256');
CALL add_file_column_if_missing('file_upload_session', 'expires_at',
  'ALTER TABLE file_upload_session ADD COLUMN expires_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT ''临时会话过期清理时间'' AFTER error_message');

ALTER TABLE file_upload_session
  MODIFY COLUMN status VARCHAR(32) NOT NULL DEFAULT 'PENDING_UPLOAD'
  COMMENT 'PENDING_UPLOAD / UPLOADING / COMPLETING / INTERRUPTED / UPLOADED / UPLOAD_FAILED / CANCELED / EXPIRED';
CALL add_file_column_if_missing('file_upload_chunk', 'chunk_sha256',
  'ALTER TABLE file_upload_chunk ADD COLUMN chunk_sha256 VARCHAR(64) DEFAULT NULL COMMENT ''分片 SHA-256'' AFTER chunk_size');

DROP PROCEDURE IF EXISTS add_file_index_if_missing;
DELIMITER //
CREATE PROCEDURE add_file_index_if_missing(
  IN tableName VARCHAR(64),
  IN indexName VARCHAR(64),
  IN alterSql TEXT
)
BEGIN
  IF NOT EXISTS (
    SELECT 1
    FROM INFORMATION_SCHEMA.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = tableName
      AND INDEX_NAME = indexName
  ) THEN
    SET @ddl = alterSql;
    PREPARE stmt FROM @ddl;
    EXECUTE stmt;
    DEALLOCATE PREPARE stmt;
  END IF;
END//
DELIMITER ;

CALL add_file_index_if_missing('document_file', 'idx_document_file_user_list',
  'ALTER TABLE document_file ADD KEY idx_document_file_user_list (user_id, knowledge_base_id, deleted, created_at DESC)');
CALL add_file_index_if_missing('document_file', 'idx_document_file_user_parse',
  'ALTER TABLE document_file ADD KEY idx_document_file_user_parse (user_id, parse_status, updated_at DESC)');
CALL add_file_index_if_missing('document_file', 'idx_document_file_user_hash',
  'ALTER TABLE document_file ADD KEY idx_document_file_user_hash (user_id, file_sha256, file_size)');
CALL add_file_index_if_missing('file_upload_session', 'idx_file_upload_session_user_status',
  'ALTER TABLE file_upload_session ADD KEY idx_file_upload_session_user_status (user_id, status, created_at DESC)');
CALL add_file_index_if_missing('file_upload_session', 'idx_file_upload_session_expire',
  'ALTER TABLE file_upload_session ADD KEY idx_file_upload_session_expire (status, expires_at)');

DROP PROCEDURE IF EXISTS add_file_column_if_missing;
DROP PROCEDURE IF EXISTS add_file_index_if_missing;

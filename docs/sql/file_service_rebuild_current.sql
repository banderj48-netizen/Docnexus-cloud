-- DocNexus 文件服务当前表结构重建脚本
-- 适用场景：已经手动删除 document_file、file_upload_session 等文件服务 6 张表后，按当前后端代码重新建表。
-- 注意：本脚本只 CREATE，不 DROP，不会删除业务数据；如果同名旧表还存在，请优先使用 file_service_upload_v1.sql 做增量补字段。

CREATE DATABASE IF NOT EXISTS `docnexus_cloud`
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_unicode_ci;

USE `docnexus_cloud`;

SET NAMES utf8mb4;

-- =========================================================
-- 一、正式文件元数据表
-- =========================================================

CREATE TABLE IF NOT EXISTS `document_file` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '数据库主键 ID',
  `file_id` VARCHAR(64) NOT NULL COMMENT '业务文件 ID',
  `user_id` BIGINT NOT NULL COMMENT '所属用户 ID',
  `knowledge_base_id` VARCHAR(64) NOT NULL DEFAULT 'default' COMMENT '知识库 ID',
  `original_name` VARCHAR(255) NOT NULL COMMENT '原始文件名',
  `file_category` VARCHAR(32) NOT NULL COMMENT '文件大类：PDF / WORD / PPT / TXT / UNKNOWN',
  `file_ext` VARCHAR(32) NOT NULL COMMENT '文件扩展名',
  `mime_type` VARCHAR(128) DEFAULT NULL COMMENT 'MIME 类型',
  `file_size` BIGINT NOT NULL COMMENT '文件大小，单位字节',
  `file_sha256` VARCHAR(64) DEFAULT NULL COMMENT '文件 SHA-256',
  `storage_type` VARCHAR(32) NOT NULL DEFAULT 'MINIO' COMMENT '存储类型',
  `bucket_name` VARCHAR(128) NOT NULL COMMENT 'MinIO bucket',
  `object_key` VARCHAR(512) NOT NULL COMMENT 'MinIO object key',
  `upload_status` VARCHAR(32) NOT NULL DEFAULT 'UPLOADED' COMMENT '上传状态：UPLOADED / DELETED',
  `parse_status` VARCHAR(32) NOT NULL DEFAULT 'NOT_REQUESTED' COMMENT '解析状态：NOT_REQUESTED / PENDING / PROCESSING / SUCCESS / FAILED',
  `index_status` VARCHAR(32) NOT NULL DEFAULT 'NONE' COMMENT '索引状态：NONE / PENDING / PROCESSING / SUCCESS / FAILED',
  `graph_status` VARCHAR(32) NOT NULL DEFAULT 'NONE' COMMENT '图谱状态：NONE / PENDING / BUILDING / SUCCESS / FAILED',
  `summary` TEXT COMMENT 'AI 摘要',
  `keywords_json` TEXT COMMENT '关键词 JSON',
  `error_message` VARCHAR(1024) DEFAULT NULL COMMENT '失败原因',
  `parse_retry_count` INT NOT NULL DEFAULT 0 COMMENT '用户手动重新解析次数',
  `current_version` INT NOT NULL DEFAULT 1 COMMENT '当前文件版本号',
  `editable` TINYINT NOT NULL DEFAULT 0 COMMENT '是否支持在线编辑',
  `content_hash` VARCHAR(64) DEFAULT NULL COMMENT '当前编辑内容 SHA-256',
  `last_saved_at` DATETIME DEFAULT NULL COMMENT '最近手动保存时间',
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

-- =========================================================
-- 二、上传会话表
-- =========================================================

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

-- =========================================================
-- 三、上传分片表
-- =========================================================

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

-- =========================================================
-- 四、文档解析任务表
-- =========================================================

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
-- 五、在线查看内容快照表
-- =========================================================

CREATE TABLE IF NOT EXISTS `document_file_content` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `file_id` VARCHAR(64) NOT NULL COMMENT '文件 ID',
  `user_id` BIGINT NOT NULL COMMENT '用户 ID',
  `version_number` INT NOT NULL COMMENT '对应 document_file.current_version',
  `content_format` VARCHAR(32) NOT NULL COMMENT 'HTML / TEXT / PDF_PREVIEW',
  `content_html` MEDIUMTEXT COMMENT '安全 HTML 内容',
  `plain_text` MEDIUMTEXT COMMENT '纯文本内容',
  `content_hash` VARCHAR(64) NOT NULL COMMENT '内容 SHA-256',
  `source_bucket` VARCHAR(128) NOT NULL COMMENT '来源 MinIO bucket',
  `source_object_key` VARCHAR(512) NOT NULL COMMENT '来源 MinIO object key',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_document_file_content_file_version` (`file_id`, `version_number`),
  KEY `idx_document_file_content_user_file` (`user_id`, `file_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='文档在线查看内容快照表';

-- =========================================================
-- 六、文档保存覆盖日志表
-- =========================================================

CREATE TABLE IF NOT EXISTS `document_content_revision_log` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `event_id` VARCHAR(64) NOT NULL COMMENT '保存事件 ID',
  `file_id` VARCHAR(64) NOT NULL COMMENT '文件 ID',
  `user_id` BIGINT NOT NULL COMMENT '用户 ID',
  `old_version` INT NOT NULL COMMENT '旧版本号',
  `new_version` INT NOT NULL COMMENT '新版本号',
  `old_object_key` VARCHAR(512) NOT NULL COMMENT '旧 MinIO 对象',
  `new_object_key` VARCHAR(512) NOT NULL COMMENT '新 MinIO 对象',
  `content_hash` VARCHAR(64) NOT NULL COMMENT '新内容 SHA-256',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_document_revision_event` (`event_id`),
  KEY `idx_document_revision_file` (`file_id`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='文档保存覆盖日志表';

-- =========================================================
-- 七、建表结果检查
-- =========================================================

SELECT TABLE_NAME
FROM INFORMATION_SCHEMA.TABLES
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME IN (
    'document_file',
    'file_upload_session',
    'file_upload_chunk',
    'document_process_task',
    'document_file_content',
    'document_content_revision_log'
  )
ORDER BY TABLE_NAME;

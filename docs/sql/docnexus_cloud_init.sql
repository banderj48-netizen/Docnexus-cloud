-- DocNexus Cloud 业务数据库初始化脚本
-- 适用数据库：MySQL 8.x
-- 执行方式：
--   mysql -uroot -p < docnexus_cloud_init.sql
-- Docker MySQL 示例：
--   docker exec -i docnexus-mysql mysql -uroot -p < docnexus_cloud_init.sql

CREATE DATABASE IF NOT EXISTS `docnexus_cloud`
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_unicode_ci;

USE `docnexus_cloud`;

-- =========================================================
-- 一、用户与认证模块
-- =========================================================

-- 用户账号表：支撑注册、登录、资料展示、密码修改、tokenVersion 失效控制。
CREATE TABLE IF NOT EXISTS `user_account` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '用户主键 ID',
  `username` VARCHAR(64) NOT NULL COMMENT '用户名，登录唯一标识',
  `password` VARCHAR(255) NOT NULL COMMENT '密码。当前项目阶段保存前端 Base64 后的密码值，后续建议升级为强哈希',
  `email` VARCHAR(128) DEFAULT NULL COMMENT '邮箱',
  `phone` VARCHAR(32) DEFAULT NULL COMMENT '手机号',
  `role` VARCHAR(32) NOT NULL DEFAULT 'USER' COMMENT '用户角色：USER / ADMIN',
  `status` VARCHAR(32) NOT NULL DEFAULT 'ENABLE' COMMENT '账号状态：ENABLE / DISABLE',
  `token_version` BIGINT NOT NULL DEFAULT 1 COMMENT '令牌版本号，修改密码或重置密码后递增，用于让旧 JWT 失效',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_account_username` (`username`),
  KEY `idx_user_account_phone` (`phone`),
  KEY `idx_user_account_email` (`email`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户账号表';

-- 用户会话表：保存 refreshToken 摘要、accessJti、设备信息和多端会话状态。
CREATE TABLE IF NOT EXISTS `user_session` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '会话主键 ID',
  `session_id` VARCHAR(64) NOT NULL COMMENT '前端持有的会话 ID',
  `user_id` BIGINT NOT NULL COMMENT '所属用户 ID',
  `refresh_token_hash` VARCHAR(128) NOT NULL COMMENT 'refreshToken 的 SHA-256 哈希值，不保存明文',
  `access_jti` VARCHAR(64) NOT NULL COMMENT '当前 accessToken 的 JWT ID',
  `token_version` BIGINT NOT NULL DEFAULT 1 COMMENT '创建或刷新该会话时使用的 tokenVersion',
  `device_id` VARCHAR(128) DEFAULT NULL COMMENT '设备 ID，前端生成或后端识别',
  `device_name` VARCHAR(128) DEFAULT NULL COMMENT '设备名称',
  `client_ip` VARCHAR(64) DEFAULT NULL COMMENT '客户端 IP',
  `user_agent` VARCHAR(512) DEFAULT NULL COMMENT '浏览器或客户端 UA',
  `status` VARCHAR(32) NOT NULL DEFAULT 'ACTIVE' COMMENT '授权会话状态：ACTIVE / EXPIRED',
  `online_status` VARCHAR(32) NOT NULL DEFAULT 'OFFLINE' COMMENT '浏览器在线历史快照：ONLINE / OFFLINE，页面实时状态以 Redis presence 为准',
  `login_at` DATETIME NOT NULL COMMENT '登录时间',
  `last_active_at` DATETIME NOT NULL COMMENT '最后授权活跃时间快照，heartbeat 不再持续写该字段',
  `access_expires_at` DATETIME NOT NULL COMMENT 'accessToken 过期时间',
  `refresh_expires_at` DATETIME NOT NULL COMMENT 'refreshToken 授权过期时间',
  `logout_at` DATETIME DEFAULT NULL COMMENT '退出登录时间',
  `offline_at` DATETIME DEFAULT NULL COMMENT '浏览器最近一次被判定离线时间',
  `expired_at` DATETIME DEFAULT NULL COMMENT '授权会话失效时间',
  `close_reason` VARCHAR(64) DEFAULT NULL COMMENT '会话失效原因：LOGOUT / REFRESH_EXPIRED / REFRESH_INVALID / TOKEN_VERSION_CHANGED / UNKNOWN',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_session_session_id` (`session_id`),
  UNIQUE KEY `uk_user_session_access_jti` (`access_jti`),
  KEY `idx_user_session_user_status_order` (`user_id`, `status`, `last_active_at` DESC, `login_at` DESC),
  KEY `idx_user_session_user_login` (`user_id`, `login_at` DESC),
  KEY `idx_user_session_user_status_refresh` (`user_id`, `status`, `refresh_expires_at`),
  KEY `idx_user_session_close_reason` (`close_reason`),
  CONSTRAINT `fk_user_session_user_id`
    FOREIGN KEY (`user_id`) REFERENCES `user_account` (`id`)
    ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户登录会话表';

-- =========================================================
-- 二、文件上传与对象存储模块
-- =========================================================

-- 文档文件表：保存上传成功后的正式文件元数据，是文件服务的最终事实来源。
CREATE TABLE IF NOT EXISTS `document_file` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '数据库主键 ID',
  `file_id` VARCHAR(64) NOT NULL COMMENT '业务文件 ID，全局唯一',
  `user_id` BIGINT NOT NULL COMMENT '所属用户 ID',
  `knowledge_base_id` VARCHAR(64) DEFAULT 'default' COMMENT '所属知识库 ID，第一阶段可使用 default',
  `original_name` VARCHAR(255) NOT NULL COMMENT '用户上传时的原始文件名',
  `file_category` VARCHAR(32) NOT NULL COMMENT '文件大类：PDF / WORD / PPT / TXT / IMAGE / UNKNOWN',
  `file_ext` VARCHAR(32) NOT NULL COMMENT '文件扩展名，例如 pdf、docx、png',
  `mime_type` VARCHAR(128) DEFAULT NULL COMMENT 'MIME 类型',
  `file_size` BIGINT NOT NULL COMMENT '文件大小，单位字节',
  `file_sha256` VARCHAR(64) DEFAULT NULL COMMENT '文件 SHA-256，用于去重和幂等判断',
  `storage_type` VARCHAR(32) NOT NULL DEFAULT 'MINIO' COMMENT '存储类型，默认 MINIO',
  `bucket_name` VARCHAR(128) NOT NULL COMMENT 'MinIO bucket 名称',
  `object_key` VARCHAR(512) NOT NULL COMMENT 'MinIO 对象 key',
  `upload_status` VARCHAR(32) NOT NULL DEFAULT 'UPLOADED' COMMENT '上传状态：UPLOADING / UPLOADED / UPLOAD_FAILED / CANCELED / DELETED',
  `knowledge_status` VARCHAR(32) NOT NULL DEFAULT 'PENDING' COMMENT '知识库状态：PENDING / INDEXING / INDEXED / FAILED',
  `graph_status` VARCHAR(32) NOT NULL DEFAULT 'PENDING' COMMENT '图谱状态：PENDING / BUILDING / BUILT / FAILED',
  `summary` TEXT COMMENT '后续 Python Agent 回写的摘要',
  `keywords_json` TEXT COMMENT '后续 Python Agent 回写的关键词 JSON',
  `error_message` VARCHAR(1024) DEFAULT NULL COMMENT '上传、解析或索引失败原因',
  `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '软删除标记：0 未删除，1 已删除',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_document_file_file_id` (`file_id`),
  KEY `idx_document_file_user_created` (`user_id`, `created_at`),
  KEY `idx_document_file_user_deleted` (`user_id`, `deleted`),
  KEY `idx_document_file_knowledge_base` (`knowledge_base_id`),
  KEY `idx_document_file_sha256` (`file_sha256`),
  CONSTRAINT `fk_document_file_user_id`
    FOREIGN KEY (`user_id`) REFERENCES `user_account` (`id`)
    ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='文档文件元数据表';

-- 文件上传会话表：保存普通上传和分片上传过程状态，取消上传时用于清理 Redis 与临时对象。
CREATE TABLE IF NOT EXISTS `file_upload_session` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '数据库主键 ID',
  `upload_id` VARCHAR(64) NOT NULL COMMENT '上传会话 ID',
  `user_id` BIGINT NOT NULL COMMENT '所属用户 ID',
  `file_id` VARCHAR(64) DEFAULT NULL COMMENT '上传成功后关联的正式文件 ID',
  `knowledge_base_id` VARCHAR(64) DEFAULT 'default' COMMENT '目标知识库 ID',
  `file_name` VARCHAR(255) NOT NULL COMMENT '原始文件名',
  `file_size` BIGINT NOT NULL COMMENT '文件总大小，单位字节',
  `file_category` VARCHAR(32) NOT NULL COMMENT '文件大类：PDF / WORD / PPT / TXT / IMAGE / UNKNOWN',
  `file_ext` VARCHAR(32) NOT NULL COMMENT '文件扩展名',
  `content_type` VARCHAR(128) DEFAULT NULL COMMENT 'MIME 类型',
  `total_chunks` INT NOT NULL DEFAULT 1 COMMENT '总分片数，普通上传为 1',
  `uploaded_chunks` INT NOT NULL DEFAULT 0 COMMENT '已上传分片数',
  `status` VARCHAR(32) NOT NULL DEFAULT 'INIT' COMMENT '上传会话状态：INIT / UPLOADING / COMPLETING / UPLOADED / FAILED / CANCELED',
  `temp_bucket_name` VARCHAR(128) DEFAULT NULL COMMENT '临时分片 bucket',
  `temp_prefix` VARCHAR(512) DEFAULT NULL COMMENT '临时分片 objectKey 前缀',
  `error_message` VARCHAR(1024) DEFAULT NULL COMMENT '上传失败原因',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_file_upload_session_upload_id` (`upload_id`),
  KEY `idx_file_upload_session_user_status` (`user_id`, `status`, `created_at`),
  KEY `idx_file_upload_session_file_id` (`file_id`),
  CONSTRAINT `fk_file_upload_session_user_id`
    FOREIGN KEY (`user_id`) REFERENCES `user_account` (`id`)
    ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='文件上传会话表';

-- 文件上传分片表：记录每个分片在 MinIO 临时目录中的位置和上传状态。
CREATE TABLE IF NOT EXISTS `file_upload_chunk` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '数据库主键 ID',
  `upload_id` VARCHAR(64) NOT NULL COMMENT '上传会话 ID',
  `chunk_index` INT NOT NULL COMMENT '分片序号，从 0 开始',
  `chunk_size` BIGINT NOT NULL COMMENT '分片大小，单位字节',
  `chunk_hash` VARCHAR(64) DEFAULT NULL COMMENT '分片 SHA-256',
  `bucket_name` VARCHAR(128) NOT NULL COMMENT '临时分片 bucket',
  `object_key` VARCHAR(512) NOT NULL COMMENT '临时分片 objectKey',
  `status` VARCHAR(32) NOT NULL DEFAULT 'UPLOADED' COMMENT '分片状态：UPLOADED / FAILED',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_file_upload_chunk_upload_index` (`upload_id`, `chunk_index`),
  KEY `idx_file_upload_chunk_upload_id` (`upload_id`),
  CONSTRAINT `fk_file_upload_chunk_upload_id`
    FOREIGN KEY (`upload_id`) REFERENCES `file_upload_session` (`upload_id`)
    ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='文件上传分片表';

-- 文档处理任务表：上传成功后创建任务，处理队列只展示该表中关联上传成功文件的记录。
CREATE TABLE IF NOT EXISTS `document_process_task` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '数据库主键 ID',
  `task_id` VARCHAR(64) NOT NULL COMMENT '处理任务 ID',
  `file_id` VARCHAR(64) NOT NULL COMMENT '关联文件 ID',
  `user_id` BIGINT NOT NULL COMMENT '所属用户 ID',
  `task_type` VARCHAR(64) NOT NULL DEFAULT 'PARSE_DOCUMENT' COMMENT '任务类型：PARSE_DOCUMENT / BUILD_VECTOR_INDEX / BUILD_KNOWLEDGE_GRAPH / DELETE_INDEX',
  `task_status` VARCHAR(32) NOT NULL DEFAULT 'WAITING' COMMENT '任务状态：WAITING / RUNNING / SUCCESS / FAILED / CANCELED',
  `stage` VARCHAR(128) NOT NULL DEFAULT '等待解析' COMMENT '当前处理阶段，直接用于前端展示',
  `progress` INT NOT NULL DEFAULT 0 COMMENT '处理进度：0-100',
  `target_service` VARCHAR(64) DEFAULT 'PYTHON_AGENT' COMMENT '目标处理服务',
  `trace_id` VARCHAR(128) DEFAULT NULL COMMENT '链路追踪 ID',
  `retry_count` INT NOT NULL DEFAULT 0 COMMENT '已重试次数',
  `max_retry` INT NOT NULL DEFAULT 3 COMMENT '最大重试次数',
  `payload_json` TEXT COMMENT '任务扩展参数 JSON',
  `error_message` VARCHAR(1024) DEFAULT NULL COMMENT '任务失败原因',
  `started_at` DATETIME DEFAULT NULL COMMENT '任务开始时间',
  `finished_at` DATETIME DEFAULT NULL COMMENT '任务完成时间',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_document_process_task_task_id` (`task_id`),
  KEY `idx_document_process_task_file_id` (`file_id`),
  KEY `idx_document_process_task_user_status` (`user_id`, `task_status`, `created_at`),
  CONSTRAINT `fk_document_process_task_file_id`
    FOREIGN KEY (`file_id`) REFERENCES `document_file` (`file_id`)
    ON DELETE CASCADE,
  CONSTRAINT `fk_document_process_task_user_id`
    FOREIGN KEY (`user_id`) REFERENCES `user_account` (`id`)
    ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='文档处理任务表';

-- =========================================================
-- 三、知识图谱第一阶段展示缓存表
-- =========================================================

-- 图谱节点表：第一阶段用于前端图谱概览展示，复杂图查询后续迁移到 Neo4j。
CREATE TABLE IF NOT EXISTS `knowledge_graph_node` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '数据库主键 ID',
  `node_id` VARCHAR(64) NOT NULL COMMENT '图谱节点业务 ID',
  `user_id` BIGINT NOT NULL COMMENT '所属用户 ID',
  `knowledge_base_id` VARCHAR(64) DEFAULT 'default' COMMENT '所属知识库 ID',
  `file_id` VARCHAR(64) DEFAULT NULL COMMENT '来源文件 ID',
  `label` VARCHAR(255) NOT NULL COMMENT '节点展示名称',
  `node_type` VARCHAR(64) NOT NULL DEFAULT 'ENTITY' COMMENT '节点类型：TOPIC / ENTITY / KEYWORD / DOCUMENT',
  `properties_json` TEXT COMMENT '扩展属性 JSON',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_knowledge_graph_node_node_id` (`node_id`),
  KEY `idx_knowledge_graph_node_user_kb` (`user_id`, `knowledge_base_id`),
  KEY `idx_knowledge_graph_node_file_id` (`file_id`),
  CONSTRAINT `fk_knowledge_graph_node_user_id`
    FOREIGN KEY (`user_id`) REFERENCES `user_account` (`id`)
    ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='知识图谱节点缓存表';

-- 图谱关系表：第一阶段用于前端关系展示，正式图遍历后续由 Neo4j 承担。
CREATE TABLE IF NOT EXISTS `knowledge_graph_edge` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '数据库主键 ID',
  `edge_id` VARCHAR(64) NOT NULL COMMENT '图谱关系业务 ID',
  `user_id` BIGINT NOT NULL COMMENT '所属用户 ID',
  `knowledge_base_id` VARCHAR(64) DEFAULT 'default' COMMENT '所属知识库 ID',
  `file_id` VARCHAR(64) DEFAULT NULL COMMENT '来源文件 ID',
  `source_node_id` VARCHAR(64) NOT NULL COMMENT '起点节点 ID',
  `target_node_id` VARCHAR(64) NOT NULL COMMENT '终点节点 ID',
  `relation_type` VARCHAR(128) NOT NULL COMMENT '关系类型',
  `weight` DECIMAL(10,4) DEFAULT 1.0000 COMMENT '关系权重',
  `properties_json` TEXT COMMENT '扩展属性 JSON',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_knowledge_graph_edge_edge_id` (`edge_id`),
  KEY `idx_knowledge_graph_edge_user_kb` (`user_id`, `knowledge_base_id`),
  KEY `idx_knowledge_graph_edge_source` (`source_node_id`),
  KEY `idx_knowledge_graph_edge_target` (`target_node_id`),
  CONSTRAINT `fk_knowledge_graph_edge_user_id`
    FOREIGN KEY (`user_id`) REFERENCES `user_account` (`id`)
    ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='知识图谱关系缓存表';

-- =========================================================
-- 四、可选初始化数据
-- =========================================================

-- 可选管理员账号：
-- 当前项目阶段密码按现有登录逻辑保存 Base64 字符串。
-- admin123 的 Base64 是 YWRtaW4xMjM=。
INSERT INTO `user_account` (
  `username`, `password`, `email`, `phone`, `role`, `status`, `token_version`, `create_time`, `update_time`
) VALUES (
  'admin', 'YWRtaW4xMjM=', 'admin@docnexus.local', '13800000000', 'ADMIN', 'ENABLE', 1, NOW(), NOW()
) ON DUPLICATE KEY UPDATE
  `role` = VALUES(`role`),
  `status` = VALUES(`status`),
  `update_time` = NOW();

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
  `knowledge_space_code` VARCHAR(64) NOT NULL DEFAULT 'personal' COMMENT '一级知识域编码',
  `knowledge_space_name` VARCHAR(128) NOT NULL DEFAULT '个人资料库' COMMENT '一级知识域名称',
  `business_category_code` VARCHAR(64) NOT NULL DEFAULT 'general' COMMENT '二级业务分类编码',
  `business_category_name` VARCHAR(128) NOT NULL DEFAULT '通用资料' COMMENT '二级业务分类名称',
  `original_name` VARCHAR(255) NOT NULL COMMENT '原始文件名',
  `display_name` VARCHAR(255) NOT NULL COMMENT '用户展示文档名，不填时使用原始文件名去扩展名',
  `file_category` VARCHAR(32) NOT NULL COMMENT '文件大类：PDF / WORD / PPT / TXT / UNKNOWN',
  `file_ext` VARCHAR(32) NOT NULL COMMENT '文件扩展名',
  `mime_type` VARCHAR(128) DEFAULT NULL COMMENT 'MIME 类型',
  `file_size` BIGINT NOT NULL COMMENT '文件大小，单位字节',
  `file_sha256` VARCHAR(64) DEFAULT NULL COMMENT '文件 SHA-256',
  `document_type` VARCHAR(64) NOT NULL DEFAULT 'GENERAL_DOCUMENT' COMMENT '文档门类',
  `document_tags_json` JSON DEFAULT NULL COMMENT '用户标签 JSON 数组',
  `course_name` VARCHAR(128) DEFAULT NULL COMMENT '课程名称',
  `project_name` VARCHAR(128) DEFAULT NULL COMMENT '项目或课题名称',
  `term_name` VARCHAR(64) DEFAULT NULL COMMENT '学期或阶段',
  `source_type` VARCHAR(64) NOT NULL DEFAULT 'USER_UPLOAD' COMMENT '资料来源',
  `storage_type` VARCHAR(32) NOT NULL DEFAULT 'MINIO' COMMENT '存储类型',
  `bucket_name` VARCHAR(128) NOT NULL COMMENT 'MinIO bucket',
  `object_key` VARCHAR(512) NOT NULL COMMENT 'MinIO object key',
  `upload_status` VARCHAR(32) NOT NULL DEFAULT 'UPLOADED' COMMENT '上传状态：UPLOADED / DELETED',
  `parse_status` VARCHAR(32) NOT NULL DEFAULT 'NOT_REQUESTED' COMMENT '解析状态：NOT_REQUESTED / PENDING / PROCESSING / SUCCESS / FAILED',
  `index_status` VARCHAR(32) NOT NULL DEFAULT 'NONE' COMMENT '索引状态：NONE / PENDING / PROCESSING / SUCCESS / FAILED',
  `graph_status` VARCHAR(32) NOT NULL DEFAULT 'NONE' COMMENT '图谱状态：NONE / PENDING / BUILDING / SUCCESS / FAILED',
  `summary` TEXT COMMENT 'AI 摘要',
  `keywords_json` JSON DEFAULT NULL COMMENT '关键词 JSON',
  `metadata_status` VARCHAR(32) NOT NULL DEFAULT 'USER_SKIPPED' COMMENT '元信息状态：USER_SKIPPED / USER_FILLED / AI_SUGGESTED',
  `ai_metadata_json` JSON DEFAULT NULL COMMENT 'AI 解析或建议的元信息 JSON',
  `parse_quality_score` INT NOT NULL DEFAULT 0 COMMENT '解析质量分',
  `parent_chunk_count` INT NOT NULL DEFAULT 0 COMMENT '父块数量',
  `child_chunk_count` INT NOT NULL DEFAULT 0 COMMENT '子块数量',
  `asset_count` INT NOT NULL DEFAULT 0 COMMENT '图片、公式、表格等资产数量',
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
  KEY `idx_document_user_space` (`user_id`, `knowledge_space_code`, `business_category_code`, `deleted`, `created_at` DESC),
  KEY `idx_document_user_type` (`user_id`, `document_type`, `parse_status`, `updated_at` DESC),
  KEY `idx_document_file_user_list` (`user_id`, `knowledge_base_id`, `deleted`, `created_at` DESC),
  KEY `idx_document_hash` (`user_id`, `file_sha256`, `file_size`),
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
  `display_name` VARCHAR(255) DEFAULT NULL COMMENT '上传阶段用户填写的展示名',
  `file_size` BIGINT NOT NULL COMMENT '文件大小',
  `file_category` VARCHAR(32) NOT NULL COMMENT '文件大类',
  `file_ext` VARCHAR(32) NOT NULL COMMENT '扩展名',
  `mime_type` VARCHAR(128) DEFAULT NULL COMMENT 'MIME 类型',
  `file_sha256` VARCHAR(64) DEFAULT NULL COMMENT '文件 SHA-256',
  `knowledge_space_code` VARCHAR(64) NOT NULL DEFAULT 'personal' COMMENT '一级知识域编码',
  `knowledge_space_name` VARCHAR(128) NOT NULL DEFAULT '个人资料库' COMMENT '一级知识域名称',
  `business_category_code` VARCHAR(64) NOT NULL DEFAULT 'general' COMMENT '二级业务分类编码',
  `business_category_name` VARCHAR(128) NOT NULL DEFAULT '通用资料' COMMENT '二级业务分类名称',
  `document_type` VARCHAR(64) NOT NULL DEFAULT 'GENERAL_DOCUMENT' COMMENT '文档门类',
  `document_tags_json` JSON DEFAULT NULL COMMENT '用户标签 JSON 数组',
  `metadata_draft_json` JSON DEFAULT NULL COMMENT '上传期间元信息草稿 JSON',
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
-- 五、文档详细元数据表
-- =========================================================

CREATE TABLE IF NOT EXISTS `document_metadata` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '数据库主键 ID',
  `file_id` VARCHAR(64) NOT NULL COMMENT '文件 ID',
  `user_id` BIGINT NOT NULL COMMENT '所属用户 ID',
  `title` VARCHAR(512) DEFAULT NULL COMMENT '文档标题',
  `authors_json` JSON DEFAULT NULL COMMENT '作者 JSON 数组',
  `institution` VARCHAR(255) DEFAULT NULL COMMENT '机构或学校',
  `journal` VARCHAR(255) DEFAULT NULL COMMENT '期刊名称',
  `conference_name` VARCHAR(255) DEFAULT NULL COMMENT '会议名称',
  `publisher` VARCHAR(255) DEFAULT NULL COMMENT '出版社',
  `publish_year` INT DEFAULT NULL COMMENT '发表或出版年份',
  `doi` VARCHAR(128) DEFAULT NULL COMMENT 'DOI',
  `isbn` VARCHAR(64) DEFAULT NULL COMMENT 'ISBN',
  `abstract_text` TEXT COMMENT '摘要',
  `reference_count` INT NOT NULL DEFAULT 0 COMMENT '参考文献数量',
  `assignment_subject` VARCHAR(128) DEFAULT NULL COMMENT '作业或实验主题',
  `report_type` VARCHAR(128) DEFAULT NULL COMMENT '报告类型',
  `requirement_type` VARCHAR(128) DEFAULT NULL COMMENT '写作要求类型',
  `form_purpose` VARCHAR(128) DEFAULT NULL COMMENT '表单用途',
  `extraction_source` VARCHAR(32) NOT NULL DEFAULT 'USER' COMMENT '元数据来源：USER / AI / IMPORT',
  `confidence` DECIMAL(5,4) DEFAULT NULL COMMENT 'AI 抽取置信度',
  `evidence_json` JSON DEFAULT NULL COMMENT 'AI 抽取证据 JSON',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_metadata_file` (`file_id`),
  KEY `idx_metadata_user_year` (`user_id`, `publish_year`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='文档详细元数据表';

-- =========================================================
-- 六、文档切块策略与父子块表
-- =========================================================

CREATE TABLE IF NOT EXISTS `document_chunk_plan` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '数据库主键 ID',
  `plan_id` VARCHAR(64) NOT NULL COMMENT '切块方案 ID',
  `file_id` VARCHAR(64) NOT NULL COMMENT '文件 ID',
  `user_id` BIGINT NOT NULL COMMENT '所属用户 ID',
  `recommended_strategy_json` JSON DEFAULT NULL COMMENT '系统推荐策略组合',
  `effective_strategy_json` JSON DEFAULT NULL COMMENT '最终生效策略，包含用户覆盖项',
  `quality_flags_json` JSON DEFAULT NULL COMMENT '低质量、扫描件、双栏等质量标记',
  `llm_chunking_enabled` TINYINT NOT NULL DEFAULT 0 COMMENT '是否启用 LLM 智能切块',
  `status` VARCHAR(32) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING / RUNNING / SUCCESS / FAILED',
  `error_message` VARCHAR(1024) DEFAULT NULL COMMENT '失败原因',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_document_chunk_plan_plan_id` (`plan_id`),
  KEY `idx_document_chunk_plan_file` (`file_id`, `status`),
  KEY `idx_document_chunk_plan_user` (`user_id`, `created_at` DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='文档切块策略表';

CREATE TABLE IF NOT EXISTS `document_chunk_parent` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '数据库主键 ID',
  `parent_chunk_id` VARCHAR(64) NOT NULL COMMENT '父块 ID',
  `file_id` VARCHAR(64) NOT NULL COMMENT '文件 ID',
  `user_id` BIGINT NOT NULL COMMENT '所属用户 ID',
  `plan_id` VARCHAR(64) DEFAULT NULL COMMENT '切块方案 ID',
  `chunk_index` INT NOT NULL COMMENT '父块序号',
  `section_path` VARCHAR(1024) DEFAULT NULL COMMENT '标题层级路径',
  `page_start` INT DEFAULT NULL COMMENT '起始页',
  `page_end` INT DEFAULT NULL COMMENT '结束页',
  `content_text` MEDIUMTEXT COMMENT '父块正文',
  `token_count` INT NOT NULL DEFAULT 0 COMMENT '估算 token 数',
  `metadata_json` JSON DEFAULT NULL COMMENT '页码、标题、表格资产等补充元数据',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_parent_chunk_id` (`parent_chunk_id`),
  KEY `idx_parent_chunk_file` (`file_id`, `chunk_index`),
  KEY `idx_parent_chunk_user` (`user_id`, `created_at` DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='文档父块表';

CREATE TABLE IF NOT EXISTS `document_chunk_child` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '数据库主键 ID',
  `child_chunk_id` VARCHAR(64) NOT NULL COMMENT '子块 ID',
  `parent_chunk_id` VARCHAR(64) NOT NULL COMMENT '父块 ID',
  `file_id` VARCHAR(64) NOT NULL COMMENT '文件 ID',
  `user_id` BIGINT NOT NULL COMMENT '所属用户 ID',
  `chunk_index` INT NOT NULL COMMENT '子块序号',
  `section_path` VARCHAR(1024) DEFAULT NULL COMMENT '继承的标题层级路径',
  `page_start` INT DEFAULT NULL COMMENT '起始页',
  `page_end` INT DEFAULT NULL COMMENT '结束页',
  `content_text` TEXT COMMENT '子块正文，用于 ES 关键词索引',
  `content_hash` VARCHAR(64) DEFAULT NULL COMMENT '子块内容 hash',
  `token_count` INT NOT NULL DEFAULT 0 COMMENT '估算 token 数',
  `embedding_status` VARCHAR(32) NOT NULL DEFAULT 'NONE' COMMENT 'NONE / PENDING / SUCCESS / FAILED',
  `milvus_collection` VARCHAR(128) DEFAULT NULL COMMENT 'Milvus collection',
  `milvus_pk` VARCHAR(128) DEFAULT NULL COMMENT 'Milvus 主键',
  `es_index` VARCHAR(128) DEFAULT NULL COMMENT 'Elasticsearch 索引名',
  `es_doc_id` VARCHAR(128) DEFAULT NULL COMMENT 'Elasticsearch 文档 ID',
  `metadata_json` JSON DEFAULT NULL COMMENT '检索过滤元数据',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_child_chunk_id` (`child_chunk_id`),
  KEY `idx_child_chunk_parent` (`parent_chunk_id`, `chunk_index`),
  KEY `idx_child_chunk_file` (`file_id`, `chunk_index`),
  KEY `idx_child_chunk_user` (`user_id`, `embedding_status`, `created_at` DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='文档子块表';

CREATE TABLE IF NOT EXISTS `document_asset` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '数据库主键 ID',
  `asset_id` VARCHAR(64) NOT NULL COMMENT '资产 ID',
  `file_id` VARCHAR(64) NOT NULL COMMENT '文件 ID',
  `user_id` BIGINT NOT NULL COMMENT '所属用户 ID',
  `parent_chunk_id` VARCHAR(64) DEFAULT NULL COMMENT '关联父块 ID',
  `child_chunk_id` VARCHAR(64) DEFAULT NULL COMMENT '关联子块 ID',
  `asset_type` VARCHAR(32) NOT NULL COMMENT 'IMAGE / TABLE / FORMULA / PAGE_SNAPSHOT',
  `page_no` INT DEFAULT NULL COMMENT '所在页码',
  `caption` VARCHAR(1024) DEFAULT NULL COMMENT '图表标题或说明',
  `text_content` MEDIUMTEXT COMMENT '表格 Markdown、公式 LaTeX 或 OCR 文本',
  `bucket_name` VARCHAR(128) DEFAULT NULL COMMENT '资产对象 bucket',
  `object_key` VARCHAR(512) DEFAULT NULL COMMENT '资产对象 key',
  `metadata_json` JSON DEFAULT NULL COMMENT '尺寸、坐标、解析器等元数据',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_document_asset_id` (`asset_id`),
  KEY `idx_document_asset_file` (`file_id`, `asset_type`),
  KEY `idx_document_asset_chunk` (`parent_chunk_id`, `child_chunk_id`),
  KEY `idx_document_asset_user` (`user_id`, `created_at` DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='文档图片表格公式资产表';

-- =========================================================
-- 七、Agent 全链路追踪与模型调用日志表
-- =========================================================

CREATE TABLE IF NOT EXISTS `agent_trace` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '数据库主键 ID',
  `trace_id` VARCHAR(64) NOT NULL COMMENT '全链路追踪 ID',
  `user_id` BIGINT NOT NULL COMMENT '所属用户 ID',
  `file_id` VARCHAR(64) DEFAULT NULL COMMENT '关联文件 ID',
  `task_id` VARCHAR(64) DEFAULT NULL COMMENT '关联任务 ID',
  `agent_name` VARCHAR(128) NOT NULL COMMENT 'Agent 名称',
  `intent` VARCHAR(128) DEFAULT NULL COMMENT '入口意图或路由结果',
  `status` VARCHAR(32) NOT NULL DEFAULT 'RUNNING' COMMENT 'RUNNING / SUCCESS / FAILED / CANCELLED',
  `input_summary` TEXT COMMENT '输入摘要',
  `output_summary` TEXT COMMENT '输出摘要',
  `token_total` INT NOT NULL DEFAULT 0 COMMENT '总 token',
  `duration_ms` BIGINT NOT NULL DEFAULT 0 COMMENT '总耗时毫秒',
  `error_message` VARCHAR(1024) DEFAULT NULL COMMENT '失败原因',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_agent_trace_id` (`trace_id`),
  KEY `idx_agent_trace_user` (`user_id`, `created_at` DESC),
  KEY `idx_agent_trace_file` (`file_id`, `task_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Agent 全链路追踪主表';

CREATE TABLE IF NOT EXISTS `agent_span` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '数据库主键 ID',
  `span_id` VARCHAR(64) NOT NULL COMMENT '步骤 ID',
  `trace_id` VARCHAR(64) NOT NULL COMMENT '全链路追踪 ID',
  `parent_span_id` VARCHAR(64) DEFAULT NULL COMMENT '父步骤 ID',
  `step_name` VARCHAR(128) NOT NULL COMMENT '步骤名称',
  `tool_name` VARCHAR(128) DEFAULT NULL COMMENT '工具名称',
  `status` VARCHAR(32) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING / RUNNING / SUCCESS / FAILED / SKIPPED / RETRYING',
  `input_summary` TEXT COMMENT '输入摘要',
  `output_summary` TEXT COMMENT '输出摘要',
  `token_total` INT NOT NULL DEFAULT 0 COMMENT '步骤 token',
  `duration_ms` BIGINT NOT NULL DEFAULT 0 COMMENT '步骤耗时毫秒',
  `retry_count` INT NOT NULL DEFAULT 0 COMMENT '重试次数',
  `error_message` VARCHAR(1024) DEFAULT NULL COMMENT '失败原因',
  `decision_json` JSON DEFAULT NULL COMMENT '模型下一步决断、工具选择和降级策略',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_agent_span_id` (`span_id`),
  KEY `idx_agent_span_trace` (`trace_id`, `created_at`),
  KEY `idx_agent_span_parent` (`parent_span_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Agent 步骤追踪表';

CREATE TABLE IF NOT EXISTS `llm_call_log` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '数据库主键 ID',
  `call_id` VARCHAR(64) NOT NULL COMMENT '模型调用 ID',
  `trace_id` VARCHAR(64) NOT NULL COMMENT '全链路追踪 ID',
  `span_id` VARCHAR(64) DEFAULT NULL COMMENT '关联步骤 ID',
  `user_id` BIGINT NOT NULL COMMENT '所属用户 ID',
  `provider` VARCHAR(64) NOT NULL COMMENT '模型供应商',
  `model_name` VARCHAR(128) NOT NULL COMMENT '模型名称',
  `prompt_tokens` INT NOT NULL DEFAULT 0 COMMENT '输入 token',
  `completion_tokens` INT NOT NULL DEFAULT 0 COMMENT '输出 token',
  `total_tokens` INT NOT NULL DEFAULT 0 COMMENT '总 token',
  `duration_ms` BIGINT NOT NULL DEFAULT 0 COMMENT '耗时毫秒',
  `status` VARCHAR(32) NOT NULL DEFAULT 'SUCCESS' COMMENT 'SUCCESS / FAILED / RETRYING',
  `request_summary` TEXT COMMENT '请求摘要，禁止保存完整敏感 prompt',
  `response_summary` TEXT COMMENT '响应摘要',
  `error_message` VARCHAR(1024) DEFAULT NULL COMMENT '失败原因',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_llm_call_id` (`call_id`),
  KEY `idx_llm_call_trace` (`trace_id`, `created_at`),
  KEY `idx_llm_call_user` (`user_id`, `created_at` DESC),
  KEY `idx_llm_call_model` (`provider`, `model_name`, `created_at` DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='大模型调用日志表';

-- =========================================================
-- 八、在线查看内容快照表
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
-- 九、文档保存覆盖日志表
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
-- 十、建表结果检查
-- =========================================================

SELECT TABLE_NAME
FROM INFORMATION_SCHEMA.TABLES
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME IN (
    'document_file',
    'file_upload_session',
    'file_upload_chunk',
    'document_process_task',
    'document_metadata',
    'document_chunk_plan',
    'document_chunk_parent',
    'document_chunk_child',
    'document_asset',
    'agent_trace',
    'agent_span',
    'llm_call_log',
    'document_file_content',
    'document_content_revision_log'
  )
ORDER BY TABLE_NAME;

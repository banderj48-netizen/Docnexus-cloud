package com.xyf.docnexus.file.util;

import java.util.UUID;

/**
 * 文件服务业务 ID 生成工具。
 */
public final class FileIdGenerator {

    private FileIdGenerator() {
    }

    /**
     * 生成正式文件 ID。
     */
    public static String fileId() {
        return "file_" + compactUuid();
    }

    /**
     * 生成上传会话 ID。
     */
    public static String uploadId() {
        return "upload_" + compactUuid();
    }

    /**
     * 生成文档处理任务 ID。
     */
    public static String taskId() {
        return "task_" + compactUuid();
    }

    /**
     * 生成无横线 UUID。
     */
    public static String compactUuid() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}

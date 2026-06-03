package com.xyf.docnexus.file.util;

import com.xyf.docnexus.file.dto.FileViewResponse;
import com.xyf.docnexus.file.entity.DocumentFile;

import java.time.format.DateTimeFormatter;

/**
 * 文件展示对象转换工具。
 */
public final class FileViewMapper {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("MM/dd HH:mm");

    private FileViewMapper() {
    }

    /**
     * 把数据库文件元数据转换为前端展示对象。
     */
    public static FileViewResponse fromDocumentFile(DocumentFile file) {
        FileViewResponse response = new FileViewResponse();
        response.setId(file.getFileId());
        response.setFileId(file.getFileId());
        response.setName(file.getOriginalName());
        response.setType(FileTypeResolver.shortType(file.getOriginalName()));
        response.setFileExt(file.getFileExt());
        response.setFileSize(file.getFileSize());
        response.setSizeText(formatSize(file.getFileSize()));
        response.setTimeText(file.getCreatedAt() == null ? "刚刚" : file.getCreatedAt().format(TIME_FORMATTER));
        response.setUploadStatus(file.getUploadStatus());
        response.setParseStatus(file.getParseStatus());
        response.setIndexStatus(file.getIndexStatus());
        response.setGraphStatus(file.getGraphStatus());
        response.setStatusText("UPLOADED".equals(file.getUploadStatus()) ? "已上传" : "已删除");
        response.setStatusTone("UPLOADED".equals(file.getUploadStatus()) ? "green" : "orange");
        response.setKnowledgeText(resolveParseText(file.getParseStatus()));
        response.setKnowledgeTone(resolveParseTone(file.getParseStatus()));
        response.setGraphText(resolveGraphText(file.getGraphStatus()));
        response.setGraphTone(resolveGraphTone(file.getGraphStatus()));
        response.setProgress("PENDING".equals(file.getParseStatus()) ? 0 : 100);
        response.setErrorMessage(file.getErrorMessage());
        response.setCreatedAt(file.getCreatedAt());
        return response;
    }

    /**
     * 格式化文件大小。
     */
    public static String formatSize(Long size) {
        if (size == null || size <= 0) {
            return "0 B";
        }
        String[] units = {"B", "KB", "MB", "GB"};
        double value = size;
        int index = 0;
        while (value >= 1024 && index < units.length - 1) {
            value = value / 1024;
            index++;
        }
        return String.format(value >= 10 || index == 0 ? "%.0f %s" : "%.1f %s", value, units[index]);
    }

    /**
     * 解析知识库展示文案。
     */
    private static String resolveParseText(String parseStatus) {
        return switch (parseStatus == null ? "" : parseStatus) {
            case "PROCESSING" -> "解析中";
            case "SUCCESS" -> "已入库";
            case "FAILED" -> "解析失败";
            default -> "待解析";
        };
    }

    /**
     * 解析知识库展示色调。
     */
    private static String resolveParseTone(String parseStatus) {
        return switch (parseStatus == null ? "" : parseStatus) {
            case "PROCESSING" -> "running";
            case "SUCCESS" -> "ready";
            case "FAILED" -> "failed";
            default -> "waiting";
        };
    }

    /**
     * 解析图谱展示文案。
     */
    private static String resolveGraphText(String graphStatus) {
        return switch (graphStatus == null ? "" : graphStatus) {
            case "BUILDING" -> "构建中";
            case "SUCCESS" -> "已构建";
            case "FAILED" -> "构建失败";
            default -> "待解析";
        };
    }

    /**
     * 解析图谱展示色调。
     */
    private static String resolveGraphTone(String graphStatus) {
        return switch (graphStatus == null ? "" : graphStatus) {
            case "BUILDING" -> "running";
            case "SUCCESS" -> "ready";
            case "FAILED" -> "failed";
            default -> "waiting";
        };
    }
}

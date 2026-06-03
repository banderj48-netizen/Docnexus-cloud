package com.xyf.docnexus.file.service;

import com.xyf.docnexus.file.dto.FileViewResponse;
import com.xyf.docnexus.file.entity.FileUploadSession;
import com.xyf.docnexus.file.mapper.FileUploadSessionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 上传失败记录服务。
 *
 * <p>失败状态使用独立事务保存，避免主上传事务回滚时丢失失败会话和队列展示项。</p>
 */
@Service
@RequiredArgsConstructor
public class FileUploadFailureService {

    private final FileUploadSessionMapper uploadSessionMapper;
    private final FileCacheService fileCacheService;

    /**
     * 独立记录上传失败状态和 Redis 队列项。
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailed(Long userId, FileUploadSession session, FileViewResponse item, String errorMessage) {
        String message = StringUtils.hasText(errorMessage) ? errorMessage : "上传失败，请稍后再试";
        uploadSessionMapper.markFailed(session.getUploadId(), message);
        item.setErrorMessage(message);
        fileCacheService.saveUploadItem(userId, item);
    }
}

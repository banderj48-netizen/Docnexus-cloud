package com.xyf.docnexus.file.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xyf.docnexus.common.VO.PageResponse;
import com.xyf.docnexus.file.config.FileServiceProperties;
import com.xyf.docnexus.file.dto.FileViewResponse;
import com.xyf.docnexus.file.entity.DocumentFile;
import com.xyf.docnexus.file.service.FileCacheService;
import com.xyf.docnexus.file.util.FileRedisKeys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 文件 Redis 缓存服务实现。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RedisFileCacheService implements FileCacheService {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final FileServiceProperties properties;

    /**
     * 读取当前文件列表缓存版本。
     */
    @Override
    public long currentVersion(Long userId, String knowledgeBaseId) {
        String key = FileRedisKeys.libraryVersionKey(userId, knowledgeBaseId);
        registerUserCacheKey(userId, key);
        String value = redisTemplate.opsForValue().get(key);
        if (value == null) {
            long initialVersion = nextVersion();
            Boolean initialized = redisTemplate.opsForValue()
                    .setIfAbsent(key, String.valueOf(initialVersion), versionTtl());
            if (Boolean.TRUE.equals(initialized)) {
                return initialVersion;
            }
            value = redisTemplate.opsForValue().get(key);
            if (value == null) {
                return initialVersion;
            }
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException exception) {
            long fixedVersion = nextVersion();
            redisTemplate.opsForValue().set(key, String.valueOf(fixedVersion), versionTtl());
            return fixedVersion;
        }
    }

    /**
     * 递增文件列表缓存版本。
     */
    @Override
    public void increaseVersion(Long userId, String knowledgeBaseId) {
        String key = FileRedisKeys.libraryVersionKey(userId, knowledgeBaseId);
        registerUserCacheKey(userId, key);
        redisTemplate.opsForValue().set(
                key,
                String.valueOf(nextVersion()),
                versionTtl()
        );
    }

    /**
     * 读取文件分页缓存。
     */
    @Override
    public PageResponse<FileViewResponse> getPage(Long userId, String knowledgeBaseId, long version, int pageNum, int pageSize) {
        String value = redisTemplate.opsForValue().get(FileRedisKeys.libraryPageKey(userId, knowledgeBaseId, version, pageNum, pageSize));
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.readValue(value, new TypeReference<PageResponse<FileViewResponse>>() {});
        } catch (Exception exception) {
            log.warn("读取文件列表缓存失败，userId={}", userId, exception);
            return null;
        }
    }

    /**
     * 写入文件分页缓存。
     */
    @Override
    public void putPage(Long userId, String knowledgeBaseId, long version, int pageNum, int pageSize, PageResponse<FileViewResponse> page) {
        try {
            String key = FileRedisKeys.libraryPageKey(userId, knowledgeBaseId, version, pageNum, pageSize);
            long base = isEmptyPage(page)
                    ? properties.getCache().getLibraryEmptyTtlSeconds()
                    : properties.getCache().getLibraryBaseTtlSeconds();
            long jitter = properties.getCache().getLibraryJitterSeconds();
            long extra = jitter <= 0 ? 0 : ThreadLocalRandom.current().nextLong(jitter + 1);
            registerUserCacheKey(userId, key);
            redisTemplate.opsForValue().set(
                    key,
                    objectMapper.writeValueAsString(page),
                    Duration.ofSeconds(base + extra)
            );
        } catch (Exception exception) {
            log.warn("写入文件列表缓存失败，userId={}", userId, exception);
        }
    }

    /**
     * 读取单文件元数据缓存。
     */
    @Override
    public DocumentFile getFileMeta(Long userId, String fileId) {
        String key = FileRedisKeys.fileMetaKey(userId, fileId);
        String value = redisTemplate.opsForValue().get(key);
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.readValue(value, DocumentFile.class);
        } catch (Exception exception) {
            log.warn("读取单文件元数据缓存失败，准备删除坏缓存，userId={}, fileId={}", userId, fileId, exception);
            redisTemplate.delete(key);
            return null;
        }
    }

    /**
     * 写入单文件元数据缓存。
     */
    @Override
    public void putFileMeta(DocumentFile file) {
        if (file == null || file.getUserId() == null || file.getFileId() == null) {
            return;
        }
        String key = FileRedisKeys.fileMetaKey(file.getUserId(), file.getFileId());
        try {
            registerUserCacheKey(file.getUserId(), key);
            redisTemplate.opsForValue().set(
                    key,
                    objectMapper.writeValueAsString(file),
                    fileMetaTtl()
            );
        } catch (Exception exception) {
            log.warn("写入单文件元数据缓存失败，userId={}, fileId={}", file.getUserId(), file.getFileId(), exception);
        }
    }

    /**
     * 原子清理当前用户的文件服务缓存。
     */
    @Override
    public long clearUserCaches(Long userId) {
        if (userId == null) {
            return 0L;
        }
        String lua = """
                local members = redis.call('smembers', KEYS[1])
                local deleted = 0
                if #members > 0 then
                    for i = 1, #members do
                        deleted = deleted + redis.call('del', members[i])
                    end
                end
                redis.call('del', KEYS[1])
                return deleted
                """;
        Long deleted = redisTemplate.execute(
                new DefaultRedisScript<>(lua, Long.class),
                List.of(FileRedisKeys.userCacheSetKey(userId))
        );
        return deleted == null ? 0L : deleted;
    }

    /**
     * 尝试获取文件列表回源锁。
     */
    @Override
    public String tryLock(String lockKey, Duration ttl) {
        String token = UUID.randomUUID().toString();
        Boolean locked = redisTemplate.opsForValue().setIfAbsent(lockKey, token, ttl);
        return Boolean.TRUE.equals(locked) ? token : null;
    }

    /**
     * 释放文件列表回源锁。
     */
    @Override
    public void unlock(String lockKey, String token) {
        if (token == null) {
            return;
        }
        String lua = """
                if redis.call('get', KEYS[1]) == ARGV[1] then
                    return redis.call('del', KEYS[1])
                end
                return 0
                """;
        redisTemplate.execute(new DefaultRedisScript<>(lua, Long.class), List.of(lockKey), token);
    }

    /**
     * 短暂等待其他线程完成缓存回填。
     */
    @Override
    public void shortWait() {
        try {
            Thread.sleep(Math.max(10L, properties.getCache().getLockWaitMillis()));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 生成文件列表缓存版本。
     *
     * <p>使用毫秒时间作为版本值，避免 version key 过期后重新从 1 开始导致旧分页缓存被误命中。</p>
     */
    private long nextVersion() {
        return System.currentTimeMillis();
    }

    /**
     * 构造版本号缓存 TTL。
     */
    private Duration versionTtl() {
        return Duration.ofSeconds(Math.max(3600L, properties.getCache().getLibraryVersionTtlSeconds()));
    }

    /**
     * 判断是否为空结果页，空页使用短 TTL 缓存以防止缓存穿透。
     */
    private boolean isEmptyPage(PageResponse<FileViewResponse> page) {
        return page == null
                || page.getTotal() == null
                || page.getTotal() == 0
                || page.getRecords() == null
                || page.getRecords().isEmpty();
    }

    /**
     * 保存上传临时状态。
     */
    @Override
    public void saveUploadItem(Long userId, FileViewResponse item) {
        try {
            String uploadSetKey = FileRedisKeys.uploadUserSetKey(userId);
            String uploadItemKey = FileRedisKeys.uploadItemKey(item.getUploadId());
            registerUserCacheKey(userId, uploadSetKey);
            registerUserCacheKey(userId, uploadItemKey);
            redisTemplate.opsForSet().add(uploadSetKey, item.getUploadId());
            redisTemplate.opsForValue().set(
                    uploadItemKey,
                    objectMapper.writeValueAsString(item),
                    Duration.ofHours(properties.getUpload().getSessionExpireHours())
            );
        } catch (Exception exception) {
            log.warn("写入上传临时状态失败，userId={}, uploadId={}", userId, item.getUploadId(), exception);
        }
    }

    /**
     * 删除上传临时状态。
     */
    @Override
    public void removeUploadItem(Long userId, String uploadId) {
        redisTemplate.opsForSet().remove(FileRedisKeys.uploadUserSetKey(userId), uploadId);
        redisTemplate.delete(FileRedisKeys.uploadItemKey(uploadId));
    }

    /**
     * 查询用户临时上传项。
     */
    @Override
    public List<FileViewResponse> listUploadItems(Long userId) {
        List<FileViewResponse> items = new ArrayList<>();
        var uploadIds = redisTemplate.opsForSet().members(FileRedisKeys.uploadUserSetKey(userId));
        if (uploadIds == null || uploadIds.isEmpty()) {
            return items;
        }
        uploadIds.forEach(uploadId -> {
            String value = redisTemplate.opsForValue().get(FileRedisKeys.uploadItemKey(uploadId));
            if (value == null) {
                redisTemplate.opsForSet().remove(FileRedisKeys.uploadUserSetKey(userId), uploadId);
                return;
            }
            try {
                items.add(objectMapper.readValue(value, FileViewResponse.class));
            } catch (Exception exception) {
                redisTemplate.delete(FileRedisKeys.uploadItemKey(uploadId));
            }
        });
        return items;
    }

    /**
     * 登记用户级缓存 Key，便于用户全会话离线后一次性原子清理。
     */
    private void registerUserCacheKey(Long userId, String cacheKey) {
        if (userId == null || cacheKey == null || cacheKey.isBlank()) {
            return;
        }
        String setKey = FileRedisKeys.userCacheSetKey(userId);
        redisTemplate.opsForSet().add(setKey, cacheKey);
        redisTemplate.expire(setKey, userCacheSetTtl());
    }

    /**
     * 构造单文件元数据缓存 TTL。
     */
    private Duration fileMetaTtl() {
        long base = Math.max(60L, properties.getCache().getFileMetaTtlSeconds());
        long jitter = Math.max(0L, properties.getCache().getLibraryJitterSeconds());
        long extra = jitter <= 0 ? 0 : ThreadLocalRandom.current().nextLong(jitter + 1);
        return Duration.ofSeconds(base + extra);
    }

    /**
     * 构造用户缓存索引集合 TTL。
     */
    private Duration userCacheSetTtl() {
        long configured = properties.getCache().getUserCacheSetTtlSeconds();
        long minSeconds = Math.max(
                properties.getCache().getLibraryVersionTtlSeconds(),
                Duration.ofHours(properties.getUpload().getSessionExpireHours()).toSeconds()
        );
        return Duration.ofSeconds(Math.max(configured, minSeconds));
    }
}

package com.xyf.docnexus.file.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xyf.docnexus.common.VO.PageResponse;
import com.xyf.docnexus.file.config.FileServiceProperties;
import com.xyf.docnexus.file.dto.FileViewResponse;
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
        redisTemplate.opsForValue().set(
                FileRedisKeys.libraryVersionKey(userId, knowledgeBaseId),
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
            long base = isEmptyPage(page)
                    ? properties.getCache().getLibraryEmptyTtlSeconds()
                    : properties.getCache().getLibraryBaseTtlSeconds();
            long jitter = properties.getCache().getLibraryJitterSeconds();
            long extra = jitter <= 0 ? 0 : ThreadLocalRandom.current().nextLong(jitter + 1);
            redisTemplate.opsForValue().set(
                    FileRedisKeys.libraryPageKey(userId, knowledgeBaseId, version, pageNum, pageSize),
                    objectMapper.writeValueAsString(page),
                    Duration.ofSeconds(base + extra)
            );
        } catch (Exception exception) {
            log.warn("写入文件列表缓存失败，userId={}", userId, exception);
        }
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
            redisTemplate.opsForSet().add(FileRedisKeys.uploadUserSetKey(userId), item.getUploadId());
            redisTemplate.opsForValue().set(
                    FileRedisKeys.uploadItemKey(item.getUploadId()),
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
}

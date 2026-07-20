package com.dataplatform.access.call.service;

import com.dataplatform.common.ratelimit.SlidingWindowRateLimitAlgorithm;
import java.time.Instant;
import java.util.Collections;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

/**
 * 访问域数据调用的 Rate Limit Service。
 * <p>业务服务接口，定义本域内部可复用的业务能力。</p>
 */
@Service
public class RateLimitService {

    private static final Logger log = LoggerFactory.getLogger(RateLimitService.class);

    private static final int DEFAULT_RATE_LIMIT = 1000; // 每分钟1000次
    private static final int WINDOW_SIZE_SECONDS = 60;
    private static final String KEY_PREFIX = "rate_limit:window:";

    private final StringRedisTemplate redisTemplate;
    private final RedisScript<Long> acquireScript;
    private final RedisScript<Long> countScript;

    public RateLimitService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.acquireScript = new DefaultRedisScript<>(SlidingWindowRateLimitAlgorithm.ACQUIRE_SCRIPT, Long.class);
        this.countScript = new DefaultRedisScript<>(SlidingWindowRateLimitAlgorithm.COUNT_SCRIPT, Long.class);
    }

    /**
     * 滑动窗口限流算法
     * @param apiKey API Key
     * @param limit 每分钟限制次数
     * @return 是否允许通过
     */
    public boolean checkRateLimit(String apiKey, int limit) {
        if (limit <= 0) {
            return false;
        }
        if (apiKey == null || apiKey.isEmpty()) {
            apiKey = "anonymous";
        }

        String windowKey = KEY_PREFIX + apiKey;
        long now = Instant.now().toEpochMilli();

        try {
            Long count = redisTemplate.execute(
                    acquireScript,
                    Collections.singletonList(windowKey),
                    String.valueOf(WINDOW_SIZE_SECONDS * 1000L),
                    String.valueOf(now),
                    SlidingWindowRateLimitAlgorithm.uniqueMember(now));
            return count != null && count <= limit;
        } catch (Exception e) {
            log.error("限流状态读取失败，拒绝本次请求: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 使用默认限制检查
     */
    public boolean checkRateLimit(String apiKey) {
        return checkRateLimit(apiKey, DEFAULT_RATE_LIMIT);
    }

    /**
     * 获取当前剩余配额
     */
    public long getRemainingQuota(String apiKey) {
        if (apiKey == null || apiKey.isEmpty()) {
            apiKey = "anonymous";
        }

        String windowKey = KEY_PREFIX + apiKey;
        long now = Instant.now().toEpochMilli();

        try {
            Long count = redisTemplate.execute(
                    countScript,
                    Collections.singletonList(windowKey),
                    String.valueOf(WINDOW_SIZE_SECONDS * 1000L),
                    String.valueOf(now));
            long used = count != null ? count : DEFAULT_RATE_LIMIT;
            return Math.max(0, DEFAULT_RATE_LIMIT - used);
        } catch (Exception e) {
            log.error("剩余额度读取失败: {}", e.getMessage());
            return 0;
        }
    }
}

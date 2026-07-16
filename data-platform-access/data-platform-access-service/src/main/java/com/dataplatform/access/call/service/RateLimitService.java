package com.dataplatform.access.call.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

/**
 * 访问域数据调用的 Rate Limit Service。
 * <p>业务服务接口，定义本域内部可复用的业务能力。</p>
 */
@Service
public class RateLimitService {

    private static final Logger log = LoggerFactory.getLogger(RateLimitService.class);

    @Autowired
    private StringRedisTemplate redisTemplate;

    private static final int DEFAULT_RATE_LIMIT = 1000; // 每分钟1000次
    private static final int WINDOW_SIZE_SECONDS = 60;

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

        String key = "rate_limit:" + apiKey;
        long now = Instant.now().getEpochSecond();
        String windowKey = key + ":" + (now / WINDOW_SIZE_SECONDS);

        try {
            Long count = redisTemplate.opsForValue().increment(windowKey);
            if (count != null && count == 1) {
                // 首次设置，设置过期时间
                redisTemplate.expire(windowKey, WINDOW_SIZE_SECONDS + 5, TimeUnit.SECONDS);
            }

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

        String key = "rate_limit:" + apiKey;
        long now = Instant.now().getEpochSecond();
        String windowKey = key + ":" + (now / WINDOW_SIZE_SECONDS);

        try {
            String value = redisTemplate.opsForValue().get(windowKey);
            long used = value != null ? Long.parseLong(value) : 0;
            return Math.max(0, DEFAULT_RATE_LIMIT - used);
        } catch (Exception e) {
            log.error("剩余额度读取失败: {}", e.getMessage());
            return 0;
        }
    }
}

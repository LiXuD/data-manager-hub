package com.dataplatform.access.call.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

/**
 * 访问域数据调用的 Rate Limit Service。
 * <p>业务服务接口，定义本域内部可复用的业务能力。</p>
 */
@Service
public class RateLimitService {

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

            if (count != null && count > limit) {
                return false;
            }
            return true;
        } catch (Exception e) {
            // Redis异常时，降级放行
            return true;
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
            return DEFAULT_RATE_LIMIT;
        }

        String key = "rate_limit:" + apiKey;
        long now = Instant.now().getEpochSecond();
        String windowKey = key + ":" + (now / WINDOW_SIZE_SECONDS);

        try {
            String value = redisTemplate.opsForValue().get(windowKey);
            long used = value != null ? Long.parseLong(value) : 0;
            return Math.max(0, DEFAULT_RATE_LIMIT - used);
        } catch (Exception e) {
            return DEFAULT_RATE_LIMIT;
        }
    }
}
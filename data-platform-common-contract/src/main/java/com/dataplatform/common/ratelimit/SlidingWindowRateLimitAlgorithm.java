package com.dataplatform.common.ratelimit;

import java.util.UUID;

/**
 * Gateway 与业务服务共用的 Redis ZSet 滑动窗口算法定义。
 *
 * <p>该类只提供无框架依赖的 Lua 脚本和请求成员生成规则，具体 Redis 客户端由调用方选择。</p>
 */
public final class SlidingWindowRateLimitAlgorithm {

    public static final String ACQUIRE_SCRIPT = """
            local key = KEYS[1]
            local window = tonumber(ARGV[1])
            local now = tonumber(ARGV[2])
            local member = ARGV[3]
            local limit = tonumber(ARGV[4])
            redis.call('ZREMRANGEBYSCORE', key, 0, now - window)
            local count = redis.call('ZCARD', key)
            if count >= limit then
                redis.call('PEXPIRE', key, window)
                return count + 1
            end
            redis.call('ZADD', key, now, member)
            redis.call('PEXPIRE', key, window)
            return count + 1
            """;

    public static final String COUNT_SCRIPT = """
            local key = KEYS[1]
            local window = tonumber(ARGV[1])
            local now = tonumber(ARGV[2])
            redis.call('ZREMRANGEBYSCORE', key, 0, now - window)
            local count = redis.call('ZCARD', key)
            if count == 0 then
                redis.call('DEL', key)
            else
                redis.call('PEXPIRE', key, window)
            end
            return count
            """;

    private SlidingWindowRateLimitAlgorithm() {
    }

    public static String uniqueMember(long timestampMillis) {
        return timestampMillis + "-" + UUID.randomUUID();
    }
}

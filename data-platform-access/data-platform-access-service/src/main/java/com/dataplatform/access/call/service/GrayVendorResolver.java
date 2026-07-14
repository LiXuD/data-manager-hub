package com.dataplatform.access.call.service;

import com.dataplatform.api.Result;
import com.dataplatform.common.util.IpUtil;
import com.dataplatform.masterdata.graylog.api.dto.GrayRuleDTO;
import com.dataplatform.masterdata.graylog.api.feign.GraylogInternalFeignClient;
import com.dataplatform.masterdata.vendor.api.dto.VendorConfigDTO;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 灰度厂商选择器。
 * <p>
 * 当同一接口有 2+ 个 active VendorConfig 且存在灰度规则时，
 * 根据 conditionType + weight 选择目标厂商。
 */
@Component
public class GrayVendorResolver {

    private static final Logger log = LoggerFactory.getLogger(GrayVendorResolver.class);
    private static final long CACHE_TTL_MS = 30_000;

    private final GraylogInternalFeignClient graylogFeignClient;
    private final ConcurrentHashMap<String, CachedRule> cache = new ConcurrentHashMap<>();

    public GrayVendorResolver(GraylogInternalFeignClient graylogFeignClient) {
        this.graylogFeignClient = graylogFeignClient;
    }

    /**
     * 解析灰度厂商选择。
     *
     * @return 选定的 VendorConfigDTO，或 null（表示使用默认 stable 厂商）
     */
    public VendorConfigDTO resolve(String interfaceCode, List<VendorConfigDTO> configs, GrayRequestContext ctx) {
        if (configs == null || configs.size() < 2) {
            return null;
        }

        GrayRuleDTO rule = getRuleWithCache(interfaceCode);
        if (rule == null) {
            return null;
        }

        // 时间窗口校验（冗余保护，masterdata 侧已有查询过滤）
        if (rule.getStartTime() != null && rule.getStartTime().isAfter(java.time.LocalDateTime.now())) {
            return null;
        }
        if (rule.getEndTime() != null && rule.getEndTime().isBefore(java.time.LocalDateTime.now())) {
            return null;
        }

        // 条件过滤
        if (!matchesCondition(rule, ctx)) {
            return null;
        }

        // 权重选择
        int weight = rule.getWeight() != null ? rule.getWeight() : 10;
        int roll = ThreadLocalRandom.current().nextInt(100);
        if (roll < weight) {
            log.debug("Gray routing: interface={}, weight={}, roll={} -> gray vendor (configs[1])", interfaceCode, weight, roll);
            return configs.get(1);
        }
        return null;
    }

    private boolean matchesCondition(GrayRuleDTO rule, GrayRequestContext ctx) {
        String conditionType = rule.getConditionType();
        String conditionValue = rule.getConditionValue();

        if (conditionType == null || "random".equals(conditionType)) {
            return true;
        }
        if (ctx == null) {
            return true;
        }

        switch (conditionType) {
            case "header":
                if (conditionValue == null || ctx.request == null) return true;
                return ctx.request.getHeader(conditionValue) != null;
            case "caller":
                if (conditionValue == null) return true;
                return String.valueOf(ctx.callerId).equals(conditionValue)
                        || (ctx.callerCode != null && ctx.callerCode.equals(conditionValue));
            case "ip":
                if (conditionValue == null || ctx.clientIp == null) return true;
                return matchesCidr(ctx.clientIp, conditionValue);
            default:
                return true;
        }
    }

    private GrayRuleDTO getRuleWithCache(String interfaceCode) {
        CachedRule cached = cache.get(interfaceCode);
        if (cached != null && System.currentTimeMillis() - cached.timestamp < CACHE_TTL_MS) {
            return cached.rule;
        }

        try {
            Result<GrayRuleDTO> result = graylogFeignClient.getActiveRule(interfaceCode);
            if (result != null && result.getData() != null) {
                GrayRuleDTO rule = result.getData();
                cache.put(interfaceCode, new CachedRule(rule, System.currentTimeMillis()));
                return rule;
            }
            cache.put(interfaceCode, new CachedRule(null, System.currentTimeMillis()));
            return null;
        } catch (Exception e) {
            log.warn("Gray rule lookup failed for {}, proceeding without gray: {}", interfaceCode, e.getMessage());
            return null;
        }
    }

    /**
     * 从请求中提取灰度上下文。
     */
    public static GrayRequestContext fromRequest(HttpServletRequest request, Long callerId, String callerCode) {
        GrayRequestContext ctx = new GrayRequestContext();
        ctx.callerId = callerId;
        ctx.callerCode = callerCode;

        if (request != null) {
            ctx.request = request;
            ctx.clientIp = IpUtil.getClientIp(request);
        }
        return ctx;
    }

    static boolean matchesCidr(String ip, String cidr) {
        try {
            String[] parts = cidr.split("/");
            InetAddress cidrAddr = InetAddress.getByName(parts[0]);
            InetAddress addr = InetAddress.getByName(ip);
            if (cidrAddr instanceof java.net.Inet4Address && addr instanceof java.net.Inet4Address) {
                int prefixLen = Integer.parseInt(parts[1]);
                int mask = prefixLen == 0 ? 0 : (-1) << (32 - prefixLen);
                int cidrInt = java.nio.ByteBuffer.wrap(cidrAddr.getAddress()).getInt();
                int addrInt = java.nio.ByteBuffer.wrap(addr.getAddress()).getInt();
                return (cidrInt & mask) == (addrInt & mask);
            }
            // IPv6: 简单前缀匹配
            byte[] cidrBytes = cidrAddr.getAddress();
            byte[] addrBytes = addr.getAddress();
            int prefixLen = Integer.parseInt(parts[1]);
            for (int i = 0; i < prefixLen / 8; i++) {
                if (cidrBytes[i] != addrBytes[i]) return false;
            }
            int remain = prefixLen % 8;
            if (remain > 0) {
                int byteIdx = prefixLen / 8;
                int mask = 0xFF << (8 - remain);
                return (cidrBytes[byteIdx] & mask) == (addrBytes[byteIdx] & mask);
            }
            return true;
        } catch (Exception e) {
            log.warn("CIDR match failed for ip={}, cidr={}: {}", ip, cidr, e.getMessage());
            return false;
        }
    }

    // --- inner types ---

    public static class GrayRequestContext {
        HttpServletRequest request;
        Long callerId;
        String callerCode;
        String clientIp;

        public GrayRequestContext() {}
    }

    private record CachedRule(GrayRuleDTO rule, long timestamp) {}
}

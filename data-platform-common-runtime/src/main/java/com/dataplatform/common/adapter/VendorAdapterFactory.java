package com.dataplatform.common.adapter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 厂商适配器工厂
 * 管理和创建厂商适配器实例
 */
public class VendorAdapterFactory {

    private static final Logger log = LoggerFactory.getLogger(VendorAdapterFactory.class);

    /** 适配器缓存 */
    private static final Map<String, VendorAdapter> ADAPTER_CACHE = new ConcurrentHashMap<>();

    /**
     * 获取适配器
     *
     * @param vendorCode 厂商编码
     * @return 适配器实例
     */
    public static VendorAdapter getAdapter(String vendorCode) {
        return ADAPTER_CACHE.computeIfAbsent(vendorCode, code -> {
            log.info("创建厂商适配器: {}", code);
            return createAdapter(code);
        });
    }

    /**
     * 创建适配器
     */
    private static VendorAdapter createAdapter(String vendorCode) {
        if ("mock_vendor".equalsIgnoreCase(vendorCode)) {
            return new MockVendorAdapter(vendorCode);
        }
        // 默认使用 HTTP 适配器
        // 未来可根据厂商类型创建不同适配器 (如: WebService, FTP 等)
        return new HttpVendorAdapter(vendorCode);
    }

    /**
     * 注册自定义适配器
     */
    public static void registerAdapter(String vendorCode, VendorAdapter adapter) {
        ADAPTER_CACHE.put(vendorCode, adapter);
        log.info("注册厂商适配器: {}", vendorCode);
    }

    /**
     * 清除缓存
     */
    public static void clearCache() {
        ADAPTER_CACHE.clear();
    }
}

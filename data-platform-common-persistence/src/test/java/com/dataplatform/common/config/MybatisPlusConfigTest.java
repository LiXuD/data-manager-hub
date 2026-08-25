package com.dataplatform.common.config;

import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class MybatisPlusConfigTest {

    @Test
    void configuresOptimisticLockBeforePagination() {
        MybatisPlusInterceptor interceptor = new MybatisPlusConfig().mybatisPlusInterceptor();

        assertEquals(2, interceptor.getInterceptors().size());
        assertInstanceOf(OptimisticLockerInnerInterceptor.class, interceptor.getInterceptors().get(0));
        assertInstanceOf(PaginationInnerInterceptor.class, interceptor.getInterceptors().get(1));
    }
}

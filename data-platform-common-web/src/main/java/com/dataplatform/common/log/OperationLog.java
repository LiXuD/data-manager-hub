package com.dataplatform.common.log;

import java.lang.annotation.*;

/**
 * 公共 Web 层操作日志的 Operation Log。
 * <p>日志治理组件，负责记录、转发或查询操作日志。</p>
 */
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface OperationLog {
    String module() default "";
    String operation() default "";
    String description() default "";
    boolean saveParams() default true;
    boolean saveResult() default true;
}

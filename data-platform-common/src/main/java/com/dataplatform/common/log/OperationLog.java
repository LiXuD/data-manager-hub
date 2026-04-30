package com.dataplatform.common.log;

import java.lang.annotation.*;

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

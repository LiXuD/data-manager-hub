package com.dataplatform.common.log;

/**
 * 公共契约层操作日志的 Operation Log Service。
 * <p>业务服务接口，定义本域内部可复用的业务能力。</p>
 */
public interface OperationLogService {
    void save(OperationLogRecord record);
}

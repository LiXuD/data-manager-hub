package com.dataplatform.common.log;

public interface OperationLogService {
    void save(OperationLogRecord record);
}

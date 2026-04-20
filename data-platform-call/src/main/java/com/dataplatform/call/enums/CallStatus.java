package com.dataplatform.call.enums;

public enum CallStatus {
    
    SUCCESS("成功"),
    FAILED("失败"),
    TIMEOUT("超时"),
    CANCELLED("取消");
    
    private final String desc;
    
    CallStatus(String desc) {
        this.desc = desc;
    }
    
    public String getDesc() {
        return desc;
    }
}
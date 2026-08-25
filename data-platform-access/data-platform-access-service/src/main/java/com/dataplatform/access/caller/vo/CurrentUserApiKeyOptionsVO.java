package com.dataplatform.access.caller.vo;

import java.util.List;

/**
 * 当前登录用户的系统关联状态及可用 API Key。
 */
public class CurrentUserApiKeyOptionsVO {

    private final boolean hasAssociatedCaller;
    private final List<CurrentUserApiKeyOptionVO> options;

    public CurrentUserApiKeyOptionsVO(
            boolean hasAssociatedCaller,
            List<CurrentUserApiKeyOptionVO> options) {
        this.hasAssociatedCaller = hasAssociatedCaller;
        this.options = options;
    }

    public boolean isHasAssociatedCaller() {
        return hasAssociatedCaller;
    }

    public List<CurrentUserApiKeyOptionVO> getOptions() {
        return options;
    }
}

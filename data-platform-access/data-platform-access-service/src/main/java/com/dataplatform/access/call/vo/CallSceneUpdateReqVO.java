package com.dataplatform.access.call.vo;

/**
 * 场景字典可变元数据。sceneCode 由创建接口确定，更新接口不接受它。
 */
public class CallSceneUpdateReqVO {

    private String sceneName;
    private String description;

    public String getSceneName() {
        return sceneName;
    }

    public void setSceneName(String sceneName) {
        this.sceneName = sceneName;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}

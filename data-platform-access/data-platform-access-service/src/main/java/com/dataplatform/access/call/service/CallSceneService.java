package com.dataplatform.access.call.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dataplatform.access.call.entity.CallScene;
import com.dataplatform.access.call.mapper.CallSceneMapper;
import com.dataplatform.common.constant.StatusConstants;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 访问域数据调用的 Call Scene Service。
 * <p>业务服务接口，定义本域内部可复用的业务能力。</p>
 */
@Service
public class CallSceneService extends ServiceImpl<CallSceneMapper, CallScene> {

    public List<CallScene> listManagedScenes() {
        LambdaQueryWrapper<CallScene> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByAsc(CallScene::getSceneCode);
        return list(wrapper);
    }

    @Transactional
    public CallScene createScene(CallScene scene) {
        if (scene == null) {
            throw CallSceneException.badRequest("CALL_SCENE_REQUIRED", "场景不能为空");
        }
        scene.setSceneCode(requiredText(scene.getSceneCode(), "CALL_SCENE_CODE_REQUIRED", "sceneCode不能为空"));
        scene.setSceneName(requiredText(scene.getSceneName(), "CALL_SCENE_NAME_REQUIRED", "sceneName不能为空"));
        scene.setStatus(normalizeStatus(scene.getStatus()));
        scene.setDescription(normalizeDescription(scene.getDescription()));
        scene.setId(null);
        scene.setDeleted(false);
        if (!save(scene)) {
            throw CallSceneException.conflict("CALL_SCENE_CREATE_FAILED", "场景创建失败");
        }
        return scene;
    }

    @Transactional
    public CallScene updateMetadata(Long id, String sceneName, String description) {
        CallScene existing = requireScene(id);
        CallScene update = new CallScene();
        update.setId(existing.getId());
        update.setSceneName(requiredText(sceneName, "CALL_SCENE_NAME_REQUIRED", "sceneName不能为空"));
        update.setDescription(normalizeDescription(description));
        if (!updateById(update)) {
            throw CallSceneException.conflict("CALL_SCENE_UPDATE_CONFLICT", "场景已被其他请求修改，请刷新后重试");
        }
        return requireScene(id);
    }

    @Transactional
    public CallScene changeStatus(Long id, String status) {
        CallScene existing = requireScene(id);
        String normalizedStatus = normalizeStatus(status);
        if (normalizedStatus.equalsIgnoreCase(existing.getStatus())) {
            return existing;
        }
        CallScene update = new CallScene();
        update.setId(id);
        update.setStatus(normalizedStatus);
        if (!updateById(update)) {
            throw CallSceneException.conflict("CALL_SCENE_STATUS_CONFLICT", "场景已被其他请求修改，请刷新后重试");
        }
        return requireScene(id);
    }

    public CallScene getActiveScene(String sceneCode) {
        if (sceneCode == null || sceneCode.trim().isEmpty()) {
            return null;
        }
        LambdaQueryWrapper<CallScene> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CallScene::getSceneCode, sceneCode.trim())
                .eq(CallScene::getStatus, StatusConstants.ACTIVE)
                .last("LIMIT 1");
        return getOne(wrapper, false);
    }

    private CallScene requireScene(Long id) {
        if (id == null || id <= 0) {
            throw CallSceneException.badRequest("CALL_SCENE_ID_INVALID", "场景ID无效");
        }
        CallScene scene = getById(id);
        if (scene == null) {
            throw CallSceneException.notFound("CALL_SCENE_NOT_FOUND", "场景不存在");
        }
        return scene;
    }

    private String requiredText(String value, String errorCode, String message) {
        if (value == null || value.trim().isEmpty()) {
            throw CallSceneException.badRequest(errorCode, message);
        }
        String normalized = value.trim();
        if ("CALL_SCENE_CODE_REQUIRED".equals(errorCode) && normalized.length() > 64) {
            throw CallSceneException.badRequest("CALL_SCENE_CODE_TOO_LONG", "sceneCode长度不能超过64个字符");
        }
        if ("CALL_SCENE_NAME_REQUIRED".equals(errorCode) && normalized.length() > 100) {
            throw CallSceneException.badRequest("CALL_SCENE_NAME_TOO_LONG", "sceneName长度不能超过100个字符");
        }
        return normalized;
    }

    private String normalizeDescription(String description) {
        if (description == null) {
            return null;
        }
        String normalized = description.trim();
        if (normalized.length() > 500) {
            throw CallSceneException.badRequest("CALL_SCENE_DESCRIPTION_TOO_LONG", "description长度不能超过500个字符");
        }
        return normalized.isEmpty() ? null : normalized;
    }

    private String normalizeStatus(String status) {
        String normalized = status == null || status.trim().isEmpty()
                ? StatusConstants.ACTIVE : status.trim().toLowerCase();
        if (!StatusConstants.ACTIVE.equals(normalized) && !StatusConstants.INACTIVE.equals(normalized)) {
            throw CallSceneException.badRequest("CALL_SCENE_STATUS_INVALID", "status必须是active或inactive");
        }
        return normalized;
    }
}

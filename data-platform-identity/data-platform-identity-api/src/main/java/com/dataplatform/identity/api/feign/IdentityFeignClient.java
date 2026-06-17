package com.dataplatform.identity.api.feign;

import com.dataplatform.api.Result;
import com.dataplatform.identity.api.dto.EncryptionReqDTO;
import com.dataplatform.identity.api.dto.LoginReqDTO;
import com.dataplatform.identity.api.dto.LoginRespDTO;
import com.dataplatform.identity.api.dto.RoleDTO;
import com.dataplatform.identity.api.dto.TenantDTO;
import com.dataplatform.identity.api.dto.UserDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

/**
 * 身份租户域远程调用的 Identity Feign Client。
 * <p>OpenFeign 远程调用契约，供其他服务依赖 api 模块完成跨域调用。</p>
 */
@FeignClient(name = "data-platform-identity", contextId = "identityFeignClient", path = "/identity")
public interface IdentityFeignClient {

    /**
     * 按租户主键查询租户信息。
     */
    @GetMapping("/tenant/{id}")
    Result<TenantDTO> getTenant(@PathVariable("id") Long id);

    /**
     * 按用户主键查询用户信息。
     */
    @GetMapping("/user/{id}")
    Result<UserDTO> getUser(@PathVariable("id") Long id);

    /**
     * 按用户名查询用户信息，用于登录和权限上下文构建。
     */
    @GetMapping("/user/byUsername/{username}")
    Result<UserDTO> getUserByUsername(@PathVariable("username") String username);

    /**
     * 按角色主键查询角色信息。
     */
    @GetMapping("/role/{id}")
    Result<RoleDTO> getRole(@PathVariable("id") Long id);

    /**
     * 执行统一登录认证并返回令牌与用户上下文。
     */
    @PostMapping("/auth/login")
    Result<LoginRespDTO> login(@RequestBody LoginReqDTO dto);

    /**
     * 对敏感字段执行统一加密。
     */
    @PostMapping("/security/encrypt")
    Result<String> encrypt(@RequestBody EncryptionReqDTO dto);

    /**
     * 对敏感字段执行统一解密。
     */
    @PostMapping("/security/decrypt")
    Result<String> decrypt(@RequestBody EncryptionReqDTO dto);
}

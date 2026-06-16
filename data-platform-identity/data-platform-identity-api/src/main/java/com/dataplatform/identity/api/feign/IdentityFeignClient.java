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

@FeignClient(name = "data-platform-identity", contextId = "identityFeignClient", path = "/identity")
public interface IdentityFeignClient {

    @GetMapping("/tenant/{id}")
    Result<TenantDTO> getTenant(@PathVariable("id") Long id);

    @GetMapping("/user/{id}")
    Result<UserDTO> getUser(@PathVariable("id") Long id);

    @GetMapping("/user/byUsername/{username}")
    Result<UserDTO> getUserByUsername(@PathVariable("username") String username);

    @GetMapping("/role/{id}")
    Result<RoleDTO> getRole(@PathVariable("id") Long id);

    @PostMapping("/auth/login")
    Result<LoginRespDTO> login(@RequestBody LoginReqDTO dto);

    @PostMapping("/security/encrypt")
    Result<String> encrypt(@RequestBody EncryptionReqDTO dto);

    @PostMapping("/security/decrypt")
    Result<String> decrypt(@RequestBody EncryptionReqDTO dto);
}

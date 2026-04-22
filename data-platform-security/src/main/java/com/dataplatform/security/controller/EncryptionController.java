package com.dataplatform.security.controller;

import com.dataplatform.common.result.Result;
import com.dataplatform.security.service.EncryptionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/security/encryption")
public class EncryptionController {

    @Autowired
    private EncryptionService encryptionService;

    @PostMapping("/encrypt")
    public Result<String> encrypt(@RequestParam String plainText,
                                  @RequestParam String tableName) {
        String encrypted = encryptionService.encrypt(plainText, tableName);
        return Result.success(encrypted);
    }

    @PostMapping("/decrypt")
    public Result<String> decrypt(@RequestParam String encryptedText,
                                  @RequestParam String tableName) {
        String decrypted = encryptionService.decrypt(encryptedText, tableName);
        return Result.success(decrypted);
    }

    @PostMapping("/rotate/{tableName}")
    public Result<Boolean> rotateKey(@PathVariable String tableName) {
        encryptionService.rotateKey(tableName);
        return Result.success(true);
    }
}
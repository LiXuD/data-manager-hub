package com.dataplatform.security.controller;

import com.dataplatform.common.result.Result;
import com.dataplatform.security.service.EncryptionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/security/encryption")
public class EncryptionController {

    @Autowired
    private EncryptionService encryptionService;

    @PostMapping("/encrypt")
    public ResponseEntity<Result<String>> encrypt(
            @RequestParam(required = false) String plainText,
            @RequestParam(required = false) String tableName) {
        // 参数验证
        if (plainText == null || plainText.trim().isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Result.error(400, "plainText不能为空"));
        }
        if (tableName == null || tableName.trim().isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Result.error(400, "tableName不能为空"));
        }

        String encrypted = encryptionService.encrypt(plainText, tableName);
        return ResponseEntity.ok(Result.success(encrypted));
    }

    @PostMapping("/decrypt")
    public ResponseEntity<Result<String>> decrypt(
            @RequestParam(required = false) String encryptedText,
            @RequestParam(required = false) String tableName) {
        if (encryptedText == null || encryptedText.trim().isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Result.error(400, "encryptedText不能为空"));
        }
        if (tableName == null || tableName.trim().isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Result.error(400, "tableName不能为空"));
        }

        String decrypted = encryptionService.decrypt(encryptedText, tableName);
        return ResponseEntity.ok(Result.success(decrypted));
    }

    @PostMapping("/rotate/{tableName}")
    public ResponseEntity<Result<Boolean>> rotateKey(@PathVariable String tableName) {
        encryptionService.rotateKey(tableName);
        return ResponseEntity.ok(Result.success(true));
    }
}
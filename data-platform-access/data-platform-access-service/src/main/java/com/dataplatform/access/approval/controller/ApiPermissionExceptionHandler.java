package com.dataplatform.access.approval.controller;

import com.dataplatform.access.approval.api.ApiPermissionException;
import com.dataplatform.api.Result;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = {
        ApiPermissionApplicationController.class,
        ApiPermissionTaskController.class,
        ApiPermissionGrantController.class
})
public class ApiPermissionExceptionHandler {

    @ExceptionHandler(ApiPermissionException.class)
    public ResponseEntity<Result<Void>> handle(ApiPermissionException exception) {
        Result<Void> result = Result.error(
                exception.getStatus().value(),
                exception.getErrorCode() + ": " + exception.getMessage());
        return ResponseEntity.status(exception.getStatus()).body(result);
    }
}

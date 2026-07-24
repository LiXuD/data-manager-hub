package com.dataplatform.identity.iam.security;

import com.dataplatform.common.result.Result;
import com.dataplatform.identity.iam.controller.PermissionController;
import com.dataplatform.identity.iam.controller.RoleController;
import com.dataplatform.identity.iam.controller.UserController;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = {
        UserController.class,
        RoleController.class,
        PermissionController.class
})
public class IamAuthorizationExceptionHandler {

    @ExceptionHandler(IamAuthorizationException.class)
    public ResponseEntity<Result<Void>> handle(IamAuthorizationException exception) {
        Result<Void> result = Result.error(
                exception.getStatus().value(),
                exception.getErrorCode() + ": " + exception.getMessage());
        return ResponseEntity.status(exception.getStatus()).body(result);
    }
}

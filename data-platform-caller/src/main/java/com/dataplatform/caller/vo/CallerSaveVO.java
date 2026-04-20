package com.dataplatform.caller.vo;

// import io.swagger.v3.oas.annotations.media.Schema;

import java.io.Serial;
import java.io.Serializable;

/**
 * Caller信息保存请求
 */

public class CallerSaveVO implements Serializable {
    
    @Serial
    private static final long serialVersionUID = 1L;
    
    private String callerCode;
    
    private String callerName;
    
    private String tenantId;
    
    private String callerType;
    
    private String description;
    
    private String contactPerson;
    
    private String contactPhone;
    
    private String status;

    // Getters and Setters
    public String getCallerCode() { return callerCode; }
    public void setCallerCode(String callerCode) { this.callerCode = callerCode; }
    public String getCallerName() { return callerName; }
    public void setCallerName(String callerName) { this.callerName = callerName; }
    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }
    public String getCallerType() { return callerType; }
    public void setCallerType(String callerType) { this.callerType = callerType; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getContactPerson() { return contactPerson; }
    public void setContactPerson(String contactPerson) { this.contactPerson = contactPerson; }
    public String getContactPhone() { return contactPhone; }
    public void setContactPhone(String contactPhone) { this.contactPhone = contactPhone; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

}
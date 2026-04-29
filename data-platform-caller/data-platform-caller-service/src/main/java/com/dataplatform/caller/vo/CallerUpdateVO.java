package com.dataplatform.caller.vo;

// import io.swagger.v3.oas.annotations.media.Schema;

import java.io.Serial;
import java.io.Serializable;

/**
 * Caller信息更新请求
 */

public class CallerUpdateVO implements Serializable {
    
    @Serial
    private static final long serialVersionUID = 1L;
    
    private Long id;
    
    private String callerCode;
    
    private String callerName;
    
    private String callerType;
    
    private String description;
    
    private String contactPerson;
    
    private String contactPhone;
    
    private String status;

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getCallerCode() { return callerCode; }
    public void setCallerCode(String callerCode) { this.callerCode = callerCode; }
    public String getCallerName() { return callerName; }
    public void setCallerName(String callerName) { this.callerName = callerName; }
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
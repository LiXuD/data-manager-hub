package com.dataplatform.identity.api.dto;

import java.io.Serializable;

public class EncryptionReqDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private String text;
    private String tableName;

    public String getText() { return text; }
    public void setText(String text) { this.text = text; }
    public String getTableName() { return tableName; }
    public void setTableName(String tableName) { this.tableName = tableName; }
}

package com.dataplatform.identity.api.dto;

import java.io.Serializable;

/**
 * 身份租户域的 Encryption Req DTO。
 * <p>跨服务契约数据对象，用于 api 模块暴露远程接口时传递稳定字段。</p>
 */
public class EncryptionReqDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private String text;
    private String tableName;

    public String getText() { return text; }
    public void setText(String text) { this.text = text; }
    public String getTableName() { return tableName; }
    public void setTableName(String tableName) { this.tableName = tableName; }
}

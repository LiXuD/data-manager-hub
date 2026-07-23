-- =====================================================
-- 迁移脚本: V002__create_api_interface
-- 描述: 创建接口定义表并修改vendor_config表
-- 创建时间: 2026-04-26
-- =====================================================

-- 1. 创建接口定义表
CREATE TABLE IF NOT EXISTS api_interface (
    id BIGSERIAL PRIMARY KEY,
    interface_code VARCHAR(64) NOT NULL UNIQUE,
    interface_name VARCHAR(128) NOT NULL,
    data_type_id BIGINT NOT NULL,
    path VARCHAR(256) DEFAULT NULL,
    description VARCHAR(512) DEFAULT NULL,
    request_schema JSONB DEFAULT NULL,
    response_schema JSONB DEFAULT NULL,
    sort INTEGER DEFAULT 0,
    status VARCHAR(32) DEFAULT 'active',
    created_by BIGINT DEFAULT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT DEFAULT NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted BOOLEAN NOT NULL DEFAULT false,
    CONSTRAINT fk_api_interface_datatype FOREIGN KEY (data_type_id) REFERENCES data_type(id)
);

-- 创建索引
CREATE INDEX IF NOT EXISTS idx_api_interface_datatype ON api_interface(data_type_id);
CREATE INDEX IF NOT EXISTS idx_api_interface_status ON api_interface(status);

-- 添加注释
COMMENT ON TABLE api_interface IS '接口定义表';
COMMENT ON COLUMN api_interface.id IS '主键ID';
COMMENT ON COLUMN api_interface.interface_code IS '接口编码';
COMMENT ON COLUMN api_interface.interface_name IS '接口名称';
COMMENT ON COLUMN api_interface.data_type_id IS '数据类型ID';
COMMENT ON COLUMN api_interface.path IS '接口路径';
COMMENT ON COLUMN api_interface.description IS '接口描述';
COMMENT ON COLUMN api_interface.request_schema IS '请求参数Schema(JSON)';
COMMENT ON COLUMN api_interface.response_schema IS '响应数据Schema(JSON)';
COMMENT ON COLUMN api_interface.sort IS '排序';
COMMENT ON COLUMN api_interface.status IS '状态: active/inactive';
COMMENT ON COLUMN api_interface.created_by IS '创建人';
COMMENT ON COLUMN api_interface.created_at IS '创建时间';
COMMENT ON COLUMN api_interface.updated_by IS '更新人';
COMMENT ON COLUMN api_interface.updated_at IS '更新时间';
COMMENT ON COLUMN api_interface.deleted IS '逻辑删除';

-- 2. 修改vendor_config表，添加interface_id字段
ALTER TABLE vendor_config ADD COLUMN IF NOT EXISTS interface_id BIGINT DEFAULT NULL;

-- 添加注释
COMMENT ON COLUMN vendor_config.interface_id IS '接口ID';

-- 3. 添加索引
CREATE INDEX IF NOT EXISTS idx_vendor_config_interface ON vendor_config(interface_id);

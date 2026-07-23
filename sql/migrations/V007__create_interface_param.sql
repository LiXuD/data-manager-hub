-- V007__create_interface_param.sql
-- 创建接口参数定义表 + vendor_config 添加 param_mapping 字段

-- 1. 创建接口参数定义表
CREATE TABLE IF NOT EXISTS interface_param (
    id BIGSERIAL PRIMARY KEY,
    interface_id BIGINT NOT NULL,
    param_name VARCHAR(64) NOT NULL,
    description VARCHAR(256),
    param_type VARCHAR(32) NOT NULL DEFAULT 'string',
    required BOOLEAN NOT NULL DEFAULT false,
    default_value VARCHAR(256),
    validation_rule VARCHAR(256),
    sort INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_interface_param_interface FOREIGN KEY (interface_id) REFERENCES api_interface(id),
    CONSTRAINT uk_interface_param UNIQUE(interface_id, param_name)
);

CREATE INDEX IF NOT EXISTS idx_interface_param_interface ON interface_param(interface_id);

COMMENT ON TABLE interface_param IS '接口参数定义表';
COMMENT ON COLUMN interface_param.id IS '主键ID';
COMMENT ON COLUMN interface_param.interface_id IS '关联接口ID';
COMMENT ON COLUMN interface_param.param_name IS '参数名';
COMMENT ON COLUMN interface_param.description IS '字段说明，如 姓名';
COMMENT ON COLUMN interface_param.param_type IS '参数类型: string/number/boolean/object/array';
COMMENT ON COLUMN interface_param.required IS '是否必填';
COMMENT ON COLUMN interface_param.default_value IS '默认值';
COMMENT ON COLUMN interface_param.validation_rule IS '校验规则表达式';
COMMENT ON COLUMN interface_param.sort IS '排序';
COMMENT ON COLUMN interface_param.created_at IS '创建时间';
COMMENT ON COLUMN interface_param.updated_at IS '更新时间';

-- 2. vendor_config 添加 param_mapping 字段
ALTER TABLE vendor_config ADD COLUMN IF NOT EXISTS param_mapping JSONB DEFAULT NULL;
COMMENT ON COLUMN vendor_config.param_mapping IS '参数映射配置(JSONB): [{"paramName":"name","targetField":"ent_name","transformExpr":null}]';

package com.dataplatform.governance.trace.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import java.util.Objects;

@TableName("data_lineage")
public class DataLineage {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("source_type")
    private String sourceType;
    @TableField("source_id")
    private Long sourceId;
    @TableField("source_name")
    private String sourceName;

    @TableField("target_type")
    private String targetType;
    @TableField("target_id")
    private Long targetId;
    @TableField("target_name")
    private String targetName;

    @TableField("relation_type")
    private String relationType;
    @TableField("transform_rule")
    private String transformRule;

    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getSourceType() { return sourceType; }
    public void setSourceType(String sourceType) { this.sourceType = sourceType; }
    public Long getSourceId() { return sourceId; }
    public void setSourceId(Long sourceId) { this.sourceId = sourceId; }
    public String getSourceName() { return sourceName; }
    public void setSourceName(String sourceName) { this.sourceName = sourceName; }
    public String getTargetType() { return targetType; }
    public void setTargetType(String targetType) { this.targetType = targetType; }
    public Long getTargetId() { return targetId; }
    public void setTargetId(Long targetId) { this.targetId = targetId; }
    public String getTargetName() { return targetName; }
    public void setTargetName(String targetName) { this.targetName = targetName; }
    public String getRelationType() { return relationType; }
    public void setRelationType(String relationType) { this.relationType = relationType; }
    public String getTransformRule() { return transformRule; }
    public void setTransformRule(String transformRule) { this.transformRule = transformRule; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DataLineage that = (DataLineage) o;
        return Objects.equals(sourceType, that.sourceType) &&
               Objects.equals(sourceId, that.sourceId) &&
               Objects.equals(targetType, that.targetType) &&
               Objects.equals(targetId, that.targetId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(sourceType, sourceId, targetType, targetId);
    }
}

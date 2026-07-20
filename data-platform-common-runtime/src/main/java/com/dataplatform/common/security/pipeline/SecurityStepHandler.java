package com.dataplatform.common.security.pipeline;

public interface SecurityStepHandler {

    SecurityStepType type();

    void validate(SecurityStepConfig step);

    Object execute(SecurityExecutionContext context, SecurityStepConfig step);
}

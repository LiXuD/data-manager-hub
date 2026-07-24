package com.dataplatform.access.approval.engine;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.flowable.engine.HistoryService;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.liquibase.LiquibaseAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class FlowableApprovalEngineAdapterTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(FlowableTestApplication.class)
            .withPropertyValues(
                    "spring.datasource.url=jdbc:h2:mem:flowable-adapter;DB_CLOSE_DELAY=-1",
                    "spring.datasource.driver-class-name=org.h2.Driver",
                    "spring.datasource.username=sa",
                    "spring.datasource.password=",
                    "spring.cloud.discovery.enabled=false",
                    "spring.cloud.nacos.discovery.enabled=false",
                    "spring.cloud.nacos.config.enabled=false",
                    "spring.autoconfigure.exclude="
                            + "org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,"
                            + "org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration,"
                            + "org.redisson.spring.starter.RedissonAutoConfigurationV2,"
                            + "cn.dev33.satoken.dao.SaTokenDaoRedisJackson",
                    "flowable.database-schema-update=true",
                    "flowable.async-executor-activate=false",
                    "flowable.check-process-definitions=false");

    @Test
    void representsParallelTasksWithoutPublishingAnArbitraryCurrentTask() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            FlowableApprovalEngineAdapter adapter = adapter(context);
            context.getBean(RepositoryService.class).createDeployment()
                    .addString("parallel-approval.bpmn20.xml", parallelProcess())
                    .deploy();

            ApprovalEnginePort.StartResult started = adapter.start(
                    "parallelApproval", "APP-001", 7L, Map.of());

            assertThat(context.getBean(RuntimeService.class).createProcessInstanceQuery()
                    .processInstanceId(started.processInstanceId())
                    .singleResult()
                    .getTenantId()).isEqualTo("7");
            assertThat(started.currentTask()).isNull();
            assertThat(adapter.getCurrentTask(started.processInstanceId())).isEmpty();
            assertThat(adapter.getCurrentTasks(started.processInstanceId()))
                    .extracting(ApprovalEnginePort.TaskSnapshot::taskDefinitionKey)
                    .containsExactlyInAnyOrder("businessReview", "securityReview");

            ApprovalEnginePort.TaskSnapshot business = adapter.getCurrentTasks(started.processInstanceId())
                    .stream()
                    .filter(task -> "businessReview".equals(task.taskDefinitionKey()))
                    .findFirst()
                    .orElseThrow();
            ApprovalEnginePort.TaskPolicy policy = adapter.getTaskPolicy(business.id());
            assertThat(policy.allowWithdraw()).isTrue();
            assertThat(policy.allowExpireAdjustment()).isFalse();
            assertThat(policy.allowedDecisions()).containsExactlyInAnyOrder("APPROVE", "REJECT");
            assertThat(policy.formFields()).singleElement().satisfies(field -> {
                assertThat(field.id()).isEqualTo("riskConfirmed");
                assertThat(field.type()).isEqualTo("boolean");
                assertThat(field.required()).isTrue();
            });
        });
    }

    @Test
    void startsLatestDefinitionWhileExistingVersionRemainsQueryable() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            RepositoryService repositoryService = context.getBean(RepositoryService.class);
            repositoryService.createDeployment()
                    .addString("versioned-v1.bpmn20.xml", versionedProcess("版本一审批"))
                    .deploy();
            repositoryService.createDeployment()
                    .addString("versioned-v2.bpmn20.xml", versionedProcess("版本二审批"))
                    .deploy();

            ApprovalEnginePort.StartResult started = adapter(context).start(
                    "versionedApproval", "APP-002", 7L, Map.of());

            assertThat(started.processDefinitionVersion()).isEqualTo(2);
            assertThat(started.currentTask()).isNotNull();
            assertThat(started.currentTask().name()).isEqualTo("版本二审批");
            assertThat(repositoryService.createProcessDefinitionQuery()
                    .processDefinitionKey("versionedApproval").count()).isEqualTo(2);
        });
    }

    @Test
    void matchesLegacyCandidateGroupsWithoutRoleCodeCaseDrift() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            context.getBean(RepositoryService.class).createDeployment()
                    .addString("legacy-uppercase-group.bpmn20.xml", legacyUppercaseGroupProcess())
                    .deploy();
            FlowableApprovalEngineAdapter adapter = adapter(context);
            ApprovalEnginePort.StartResult started = adapter.start(
                    "legacyUppercaseGroup", "APP-LEGACY-GROUP", 7L, Map.of());

            assertThat(adapter.findTasks("22", Set.of("admin")))
                    .extracting(ApprovalEnginePort.TaskSnapshot::id)
                    .containsExactly(started.currentTask().id());
            assertThat(adapter.canClaim(started.currentTask().id(), Set.of("AdMiN")))
                    .isTrue();
        });
    }

    @Test
    void advancesSequentialApprovalOneNodeAtATime() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            context.getBean(RepositoryService.class).createDeployment()
                    .addString("sequential-approval.bpmn20.xml", sequentialProcess())
                    .deploy();
            ApprovalEnginePort.StartResult started = adapter(context).start(
                    "sequentialApproval", "APP-003", 7L, Map.of());

            ApprovalEnginePort.TaskSnapshot first = started.currentTask();
            assertThat(first).isNotNull();
            assertThat(first.taskDefinitionKey()).isEqualTo("businessReview");
            context.getBean(TaskService.class).claim(first.id(), "approver-1");
            context.getBean(TaskService.class).complete(first.id());

            ApprovalEnginePort.TaskSnapshot second = adapter(context)
                    .getCurrentTask(started.processInstanceId()).orElseThrow();
            assertThat(second.taskDefinitionKey()).isEqualTo("securityReview");
            context.getBean(TaskService.class).claim(second.id(), "approver-2");
            context.getBean(TaskService.class).complete(second.id());
            assertThat(adapter(context).getCurrentTask(started.processInstanceId())).isEmpty();
        });
    }

    @Test
    void restoresActiveTasksAndHistoryAfterEngineRestart() {
        AtomicReference<String> processInstanceId = new AtomicReference<>();
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            context.getBean(RepositoryService.class).createDeployment()
                    .addString("restart-approval.bpmn20.xml", versionedProcess("重启恢复审批"))
                    .deploy();
            processInstanceId.set(adapter(context).start(
                    "versionedApproval", "APP-RESTART", 7L, Map.of()).processInstanceId());
        });

        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(adapter(context).getCurrentTask(processInstanceId.get()))
                    .get()
                    .extracting(ApprovalEnginePort.TaskSnapshot::name)
                    .isEqualTo("重启恢复审批");
            assertThat(adapter(context).history(processInstanceId.get())).isNotEmpty();
        });
    }

    @Test
    void routesToDifferentApprovalNodesThroughConditionalGateway() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            context.getBean(RepositoryService.class).createDeployment()
                    .addString("conditional-approval.bpmn20.xml", conditionalProcess())
                    .deploy();
            FlowableApprovalEngineAdapter adapter = adapter(context);

            ApprovalEnginePort.StartResult highRisk = adapter.start(
                    "conditionalApproval", "APP-HIGH", 7L, Map.of("riskLevel", "HIGH"));
            ApprovalEnginePort.StartResult lowRisk = adapter.start(
                    "conditionalApproval", "APP-LOW", 7L, Map.of("riskLevel", "LOW"));

            assertThat(highRisk.currentTask().taskDefinitionKey()).isEqualTo("securityReview");
            assertThat(lowRisk.currentTask().taskDefinitionKey()).isEqualTo("businessReview");
        });
    }

    @Test
    void exposesAllParallelMultiInstanceApprovalTasksUntilCountersComplete() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            context.getBean(RepositoryService.class).createDeployment()
                    .addString("multi-instance-approval.bpmn20.xml", multiInstanceProcess())
                    .deploy();
            FlowableApprovalEngineAdapter adapter = adapter(context);
            ApprovalEnginePort.StartResult started = adapter.start(
                    "multiInstanceApproval",
                    "APP-MULTI",
                    7L,
                    Map.of("reviewers", List.of("approver-1", "approver-2")));

            assertThat(started.currentTask()).isNull();
            assertThat(adapter.getCurrentTasks(started.processInstanceId()))
                    .extracting(ApprovalEnginePort.TaskSnapshot::assignee)
                    .containsExactlyInAnyOrder("approver-1", "approver-2");

            for (ApprovalEnginePort.TaskSnapshot task
                    : adapter.getCurrentTasks(started.processInstanceId())) {
                context.getBean(TaskService.class).complete(task.id());
            }
            assertThat(adapter.getCurrentTasks(started.processInstanceId())).isEmpty();
        });
    }

    private FlowableApprovalEngineAdapter adapter(
            org.springframework.context.ApplicationContext context) {
        return new FlowableApprovalEngineAdapter(
                context.getBean(RuntimeService.class),
                context.getBean(TaskService.class),
                context.getBean(RepositoryService.class),
                context.getBean(HistoryService.class));
    }

    private String parallelProcess() {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                             xmlns:flowable="http://flowable.org/bpmn"
                             targetNamespace="http://dataplatform.com/access/test">
                  <process id="parallelApproval" isExecutable="true">
                    <startEvent id="start"/>
                    <sequenceFlow id="toFork" sourceRef="start" targetRef="fork"/>
                    <parallelGateway id="fork"/>
                    <sequenceFlow id="toBusiness" sourceRef="fork" targetRef="businessReview"/>
                    <sequenceFlow id="toSecurity" sourceRef="fork" targetRef="securityReview"/>
                    <userTask id="businessReview" name="业务审批" flowable:candidateGroups="admin">
                      <extensionElements>
                        <flowable:formProperty id="_allowWithdraw" type="boolean" default="true" readable="false" writable="false"/>
                        <flowable:formProperty id="_allowExpireAdjustment" type="boolean" default="false" readable="false" writable="false"/>
                        <flowable:formProperty id="decision" name="审批决定" type="enum" required="true">
                          <flowable:value id="APPROVE" name="同意"/>
                          <flowable:value id="REJECT" name="驳回"/>
                        </flowable:formProperty>
                        <flowable:formProperty id="riskConfirmed" name="风险已确认" type="boolean" required="true"/>
                      </extensionElements>
                    </userTask>
                    <userTask id="securityReview" name="安全审批" flowable:candidateGroups="security">
                      <extensionElements>
                        <flowable:formProperty id="_allowWithdraw" type="boolean" default="false" readable="false" writable="false"/>
                        <flowable:formProperty id="decision" name="审批决定" type="enum" required="true">
                          <flowable:value id="APPROVE" name="同意"/>
                          <flowable:value id="REJECT" name="驳回"/>
                        </flowable:formProperty>
                      </extensionElements>
                    </userTask>
                    <sequenceFlow id="businessToJoin" sourceRef="businessReview" targetRef="join"/>
                    <sequenceFlow id="securityToJoin" sourceRef="securityReview" targetRef="join"/>
                    <parallelGateway id="join"/>
                    <sequenceFlow id="toEnd" sourceRef="join" targetRef="end"/>
                    <endEvent id="end"/>
                  </process>
                </definitions>
                """;
    }

    private String legacyUppercaseGroupProcess() {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                             xmlns:flowable="http://flowable.org/bpmn"
                             targetNamespace="http://dataplatform.com/access/test">
                  <process id="legacyUppercaseGroup" isExecutable="true">
                    <startEvent id="start"/>
                    <sequenceFlow id="toReview" sourceRef="start" targetRef="review"/>
                    <userTask id="review" name="历史管理员审批" flowable:candidateGroups="ADMIN"/>
                    <sequenceFlow id="toEnd" sourceRef="review" targetRef="end"/>
                    <endEvent id="end"/>
                  </process>
                </definitions>
                """;
    }

    private String versionedProcess(String taskName) {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                             xmlns:flowable="http://flowable.org/bpmn"
                             targetNamespace="http://dataplatform.com/access/test">
                  <process id="versionedApproval" isExecutable="true">
                    <startEvent id="start"/>
                    <sequenceFlow id="toTask" sourceRef="start" targetRef="review"/>
                    <userTask id="review" name="%s" flowable:candidateGroups="admin">
                      <extensionElements>
                        <flowable:formProperty id="decision" name="审批决定" type="enum" required="true">
                          <flowable:value id="APPROVE" name="同意"/>
                        </flowable:formProperty>
                      </extensionElements>
                    </userTask>
                    <sequenceFlow id="toEnd" sourceRef="review" targetRef="end"/>
                    <endEvent id="end"/>
                  </process>
                </definitions>
                """.formatted(taskName);
    }

    private String sequentialProcess() {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                             xmlns:flowable="http://flowable.org/bpmn"
                             targetNamespace="http://dataplatform.com/access/test">
                  <process id="sequentialApproval" isExecutable="true">
                    <startEvent id="start"/>
                    <sequenceFlow id="toBusiness" sourceRef="start" targetRef="businessReview"/>
                    <userTask id="businessReview" name="业务审批" flowable:candidateGroups="business"/>
                    <sequenceFlow id="toSecurity" sourceRef="businessReview" targetRef="securityReview"/>
                    <userTask id="securityReview" name="安全审批" flowable:candidateGroups="security"/>
                    <sequenceFlow id="toEnd" sourceRef="securityReview" targetRef="end"/>
                    <endEvent id="end"/>
                  </process>
                </definitions>
                """;
    }

    private String conditionalProcess() {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                             xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                             targetNamespace="http://dataplatform.com/access/test">
                  <process id="conditionalApproval" isExecutable="true">
                    <startEvent id="start"/>
                    <sequenceFlow id="toGateway" sourceRef="start" targetRef="riskGateway"/>
                    <exclusiveGateway id="riskGateway" default="toBusiness"/>
                    <sequenceFlow id="toSecurity" sourceRef="riskGateway" targetRef="securityReview">
                      <conditionExpression xsi:type="tFormalExpression"><![CDATA[${riskLevel == 'HIGH'}]]></conditionExpression>
                    </sequenceFlow>
                    <sequenceFlow id="toBusiness" sourceRef="riskGateway" targetRef="businessReview"/>
                    <userTask id="securityReview" name="安全审批"/>
                    <userTask id="businessReview" name="业务审批"/>
                    <sequenceFlow id="securityToEnd" sourceRef="securityReview" targetRef="end"/>
                    <sequenceFlow id="businessToEnd" sourceRef="businessReview" targetRef="end"/>
                    <endEvent id="end"/>
                  </process>
                </definitions>
                """;
    }

    private String multiInstanceProcess() {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                             xmlns:flowable="http://flowable.org/bpmn"
                             targetNamespace="http://dataplatform.com/access/test">
                  <process id="multiInstanceApproval" isExecutable="true">
                    <startEvent id="start"/>
                    <sequenceFlow id="toReview" sourceRef="start" targetRef="parallelReview"/>
                    <userTask id="parallelReview" name="并行会签" flowable:assignee="${reviewer}">
                      <multiInstanceLoopCharacteristics isSequential="false"
                          flowable:collection="reviewers" flowable:elementVariable="reviewer">
                        <completionCondition>${nrOfCompletedInstances == nrOfInstances}</completionCondition>
                      </multiInstanceLoopCharacteristics>
                    </userTask>
                    <sequenceFlow id="toEnd" sourceRef="parallelReview" targetRef="end"/>
                    <endEvent id="end"/>
                  </process>
                </definitions>
                """;
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration(exclude = LiquibaseAutoConfiguration.class)
    static class FlowableTestApplication {
    }
}

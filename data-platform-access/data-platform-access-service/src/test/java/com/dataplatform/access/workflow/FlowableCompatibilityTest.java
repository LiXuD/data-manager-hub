package com.dataplatform.access.workflow;

import org.flowable.engine.HistoryService;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.history.HistoricProcessInstance;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.task.api.Task;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.liquibase.LiquibaseAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class FlowableCompatibilityTest {

    private static final String PROCESS_KEY = "apiPermissionApprovalCompatibility";
    private static final String APPROVER_GROUP = "api_interface_approver";

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(FlowablePocApplication.class)
            .withPropertyValues(
                    "spring.datasource.url=jdbc:h2:mem:flowable-compatibility;DB_CLOSE_DELAY=-1",
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
                    "flowable.check-process-definitions=false"
            );

    @Test
    void shouldStartEngineAndCompleteCandidateGroupApproval() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();

            RepositoryService repositoryService = context.getBean(RepositoryService.class);
            RuntimeService runtimeService = context.getBean(RuntimeService.class);
            TaskService taskService = context.getBean(TaskService.class);
            HistoryService historyService = context.getBean(HistoryService.class);

            repositoryService.createDeployment()
                    .name("Flowable compatibility POC")
                    .addString("api-permission-approval-compatibility.bpmn20.xml", approvalProcess())
                    .deploy();

            ProcessInstance processInstance = runtimeService.startProcessInstanceByKey(
                    PROCESS_KEY,
                    "application-compatibility-001",
                    Map.of("applicantId", 1001L)
            );

            Task candidateTask = taskService.createTaskQuery()
                    .processInstanceId(processInstance.getId())
                    .taskCandidateGroup(APPROVER_GROUP)
                    .singleResult();
            assertThat(candidateTask).isNotNull();
            assertThat(candidateTask.getTaskDefinitionKey()).isEqualTo("approvalTask");

            taskService.claim(candidateTask.getId(), "approver-2001");
            Task claimedTask = taskService.createTaskQuery()
                    .taskId(candidateTask.getId())
                    .singleResult();
            assertThat(claimedTask.getAssignee()).isEqualTo("approver-2001");

            taskService.complete(candidateTask.getId(), Map.of("decision", "APPROVE"));

            assertThat(runtimeService.createProcessInstanceQuery()
                    .processInstanceId(processInstance.getId())
                    .singleResult()).isNull();

            HistoricProcessInstance historicProcess = historyService.createHistoricProcessInstanceQuery()
                    .processInstanceId(processInstance.getId())
                    .singleResult();
            assertThat(historicProcess).isNotNull();
            assertThat(historicProcess.getEndTime()).isNotNull();
        });
    }

    private String approvalProcess() {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                             xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                             xmlns:flowable="http://flowable.org/bpmn"
                             targetNamespace="http://dataplatform.com/access">
                  <process id="%s" name="API permission approval compatibility" isExecutable="true">
                    <startEvent id="start"/>
                    <sequenceFlow id="flow-start-task" sourceRef="start" targetRef="approvalTask"/>
                    <userTask id="approvalTask"
                              name="API permission approval"
                              flowable:candidateGroups="%s"/>
                    <sequenceFlow id="flow-task-gateway" sourceRef="approvalTask" targetRef="decisionGateway"/>
                    <exclusiveGateway id="decisionGateway"/>
                    <sequenceFlow id="flow-approved" sourceRef="decisionGateway" targetRef="approvedEnd">
                      <conditionExpression xsi:type="tFormalExpression"><![CDATA[${decision == 'APPROVE'}]]></conditionExpression>
                    </sequenceFlow>
                    <sequenceFlow id="flow-rejected" sourceRef="decisionGateway" targetRef="rejectedEnd">
                      <conditionExpression xsi:type="tFormalExpression"><![CDATA[${decision == 'REJECT'}]]></conditionExpression>
                    </sequenceFlow>
                    <endEvent id="approvedEnd" name="Approved"/>
                    <endEvent id="rejectedEnd" name="Rejected"/>
                  </process>
                </definitions>
                """.formatted(PROCESS_KEY, APPROVER_GROUP);
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration(exclude = LiquibaseAutoConfiguration.class)
    static class FlowablePocApplication {
    }
}

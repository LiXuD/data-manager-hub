package com.dataplatform.common.log;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dataplatform.common.config.OperationLogAutoConfiguration;
import com.dataplatform.common.constant.StatusConstants;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class OperationLogAutoConfigurationTest {

    @Test
    void autoConfigurationRegistersAspectOutsideComponentScan() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(OperationLogAutoConfiguration.class))
                .withBean(ObjectMapper.class)
                .run(context -> assertThat(context).hasSingleBean(OperationLogAspect.class));
    }

    @Test
    void aspectResolvesAndDelegatesToOperationLogService() throws Throwable {
        OperationLogService logService = mock(OperationLogService.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<OperationLogService> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(logService);
        OperationLogAspect aspect = new OperationLogAspect(provider, new ObjectMapper());
        ProceedingJoinPoint point = mock(ProceedingJoinPoint.class);
        MethodSignature signature = mock(MethodSignature.class);
        when(point.getSignature()).thenReturn(signature);
        when(signature.getDeclaringTypeName()).thenReturn(LoggedTarget.class.getName());
        when(signature.getName()).thenReturn("run");
        when(point.getArgs()).thenReturn(new Object[]{"input"});
        when(point.proceed()).thenReturn("ok");
        OperationLog annotation = LoggedTarget.class.getDeclaredMethod("run", String.class)
                .getAnnotation(OperationLog.class);

        assertEquals("ok", aspect.around(point, annotation));

        ArgumentCaptor<OperationLogRecord> captor = ArgumentCaptor.forClass(OperationLogRecord.class);
        verify(logService).save(captor.capture());
        assertEquals("test", captor.getValue().getModule());
        assertEquals("run", captor.getValue().getOperation());
        assertEquals(StatusConstants.SUCCESS, captor.getValue().getStatus());
    }

    private static class LoggedTarget {
        @OperationLog(module = "test", operation = "run", saveResult = true)
        public String run(String input) {
            return input;
        }
    }
}

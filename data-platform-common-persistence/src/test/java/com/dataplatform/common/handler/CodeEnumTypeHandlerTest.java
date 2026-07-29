package com.dataplatform.common.handler;

import com.dataplatform.common.enums.AlertStatus;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import org.apache.ibatis.type.JdbcType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CodeEnumTypeHandlerTest {

    private final CodeEnumTypeHandler<AlertStatus> handler = new CodeEnumTypeHandler<>(AlertStatus.class);

    @Test
    void writesPersistedCodeInsteadOfEnumName() throws Exception {
        PreparedStatement statement = mock(PreparedStatement.class);

        handler.setNonNullParameter(statement, 1, AlertStatus.ACTIVE, JdbcType.VARCHAR);

        verify(statement).setString(1, "active");
    }

    @Test
    void readsPersistedCodeBackIntoEnum() throws Exception {
        ResultSet resultSet = mock(ResultSet.class);
        when(resultSet.getString("status")).thenReturn("active");

        assertThat(handler.getNullableResult(resultSet, "status")).isEqualTo(AlertStatus.ACTIVE);
    }
}

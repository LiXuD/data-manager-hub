package com.dataplatform.access.caller.handler;

import com.dataplatform.common.enums.ApiKeyStatus;
import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedTypes;

@MappedTypes(ApiKeyStatus.class)
public class ApiKeyStatusTypeHandler extends BaseTypeHandler<ApiKeyStatus> {

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, ApiKeyStatus parameter, JdbcType jdbcType)
            throws SQLException {
        ps.setString(i, parameter.getCode());
    }

    @Override
    public ApiKeyStatus getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return ApiKeyStatus.fromCode(rs.getString(columnName));
    }

    @Override
    public ApiKeyStatus getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return ApiKeyStatus.fromCode(rs.getString(columnIndex));
    }

    @Override
    public ApiKeyStatus getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return ApiKeyStatus.fromCode(cs.getString(columnIndex));
    }
}

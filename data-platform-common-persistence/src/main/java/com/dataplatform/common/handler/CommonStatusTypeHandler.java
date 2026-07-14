package com.dataplatform.common.handler;

import com.dataplatform.common.enums.CommonStatus;
import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedTypes;

@MappedTypes(CommonStatus.class)
public class CommonStatusTypeHandler extends BaseTypeHandler<CommonStatus> {

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, CommonStatus parameter, JdbcType jdbcType)
            throws SQLException {
        ps.setString(i, parameter.getCode());
    }

    @Override
    public CommonStatus getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return CommonStatus.fromCode(rs.getString(columnName));
    }

    @Override
    public CommonStatus getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return CommonStatus.fromCode(rs.getString(columnIndex));
    }

    @Override
    public CommonStatus getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return CommonStatus.fromCode(cs.getString(columnIndex));
    }
}

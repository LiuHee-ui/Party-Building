package com.hongmai.common.crypto;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedJdbcTypes;
import org.apache.ibatis.type.MappedTypes;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * 敏感字段加密 TypeHandler（承接 N2）。
 * 数据库列类型为 VARBINARY，Java 侧为 String，落库自动加密、出库自动解密。
 * 注意：解密属敏感数据访问，调用方须在同一事务内写 t_sensitive_access_log。
 */
@MappedTypes(String.class)
@MappedJdbcTypes(JdbcType.VARBINARY)
public class MobileCipherHandler extends BaseTypeHandler<String> {

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, String parameter, JdbcType jdbcType)
            throws SQLException {
        ps.setBytes(i, CryptoUtil.encryptBytes(parameter));
    }

    @Override
    public String getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return CryptoUtil.decryptBytes(rs.getBytes(columnName));
    }

    @Override
    public String getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return CryptoUtil.decryptBytes(rs.getBytes(columnIndex));
    }

    @Override
    public String getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return CryptoUtil.decryptBytes(cs.getBytes(columnIndex));
    }
}

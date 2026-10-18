package com.nexus.report.mapper;

import org.springframework.jdbc.core.ColumnMapRowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;

/** 时间列统一由 JDBC 驱动映射，保持连接会话的时间语义。 */
public final class TimestampColumnMapRowMapper extends ColumnMapRowMapper {
    public static final TimestampColumnMapRowMapper INSTANCE = new TimestampColumnMapRowMapper();

    private TimestampColumnMapRowMapper() {}

    @Override
    protected Object getColumnValue(ResultSet result, int index) throws SQLException {
        if (result.getMetaData().getColumnType(index) == Types.TIMESTAMP)
            return result.getTimestamp(index);
        return super.getColumnValue(result, index);
    }
}

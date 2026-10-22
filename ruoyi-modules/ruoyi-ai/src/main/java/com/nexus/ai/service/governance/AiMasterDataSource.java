package com.nexus.ai.service.governance;

import com.baomidou.dynamic.datasource.DynamicRoutingDataSource;

import org.springframework.jdbc.core.*;

import java.sql.*;
import java.util.Map;

import javax.sql.DataSource;

/** 治理元数据固定走物理master，并统一驱动返回的日期类型。 */
public final class AiMasterDataSource {
    private AiMasterDataSource() {}

    public static DataSource resolve(DataSource source) {
        return source instanceof DynamicRoutingDataSource routing
                ? routing.getDataSource("master")
                : source;
    }

    public static JdbcTemplate jdbc(DataSource source) {
        return new JdbcTemplate(resolve(source)) {
            @Override
            protected RowMapper<Map<String, Object>> getColumnMapRowMapper() {
                return new ColumnMapRowMapper() {
                    @Override
                    protected Object getColumnValue(ResultSet result, int index)
                            throws SQLException {
                        int type = result.getMetaData().getColumnType(index);
                        if (type == Types.TIMESTAMP || type == Types.TIMESTAMP_WITH_TIMEZONE)
                            return result.getTimestamp(index);
                        if (type == Types.DATE) return result.getDate(index);
                        return super.getColumnValue(result, index);
                    }
                };
            }
        };
    }
}

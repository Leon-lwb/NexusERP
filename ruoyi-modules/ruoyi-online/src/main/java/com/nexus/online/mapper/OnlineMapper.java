package com.nexus.online.mapper;

import java.util.List;
import java.util.Map;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.DeleteProvider;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.toolkit.Constants;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nexus.common.mybatis.core.mapper.BaseMapperPlus;
import com.nexus.generator.domain.GenTableColumn;
import com.nexus.online.domain.vo.DictVo;
import com.nexus.online.domain.vo.OnlFieldVo;

/**
 * 在线Mapper接口
 *
 * @author wanghuan
 * @date 2024-12-30
 */

public interface OnlineMapper extends BaseMapperPlus<GenTableColumn, OnlFieldVo> {
	//根据字典类型获取字典数据
    @Select("SELECT dict_value as id,dict_label as name FROM sys_dict_data WHERE dict_type = #{dictType} AND tenant_id = #{tenandId}")
    List<DictVo> getDictData(@Param("dictType") String dictType, @Param("tenandId") String tenandId);
    //根据表名分页获取表数据
    @Select("SELECT * FROM ${tableName} ${ew.getCustomSqlSegment}")
    Page<Map> getOnlineData(@Param("tableName") String tableName, @Param("page") Page<Map> page, @Param(Constants.WRAPPER) Wrapper<Map> queryWrapper);
    /**
     * 动态更新表数据
     * @param tableName 表名
     * @param data 更新的数据（字段名 -> 字段值）
     * @param condition 条件（字段名 -> 条件值）
     * @return 影响的行数
     */
    int dynamicUpdate(@Param("tableName") String tableName,
                      @Param("data") Map<String, Object> data,
                      @Param("condition") Map<String, Object> condition);
    /**
     * 动态插入表数据
     * @param tableName 表名
     * @param data 更新的数据（字段名 -> 字段值）
     * @return 影响的行数
     */
    int insertDynamic(@Param("tableName") String tableName,
                      @Param("data") Map<String, Object> data);
    /**
     * 删除online表数据
     * @param tableName 表名
     * @param pkColumn 删除的主键名称
     * @param ids 删除的主键串
     * @return 影响的行数
     */
    void deleteDynamicTable(
            @Param("tableName") String tableName,
            @Param("pkColumn") String pkColumn,
            @Param("ids") List<Long> ids);
    /**
     * 查询online表的id详细数据
     * @param tableName 表名
     * @param pkColumn 查询的主键名称
     * @param id 查询的主键
     * @return 查询的map数据
     */
    Map<String, Object> selectDynamicTableById(
    	    @Param("tableName") String tableName,
    	    @Param("pkName") String pkName,
    	    @Param("id") Long id
    	);

    /**
     * 根据外键删除子表数据
     * @param subTableName 子表名
     * @param fkName 外键字段名
     * @param mainId 主表ID值
     */
    void deleteSubByForeignKey(
        @Param("subTableName") String subTableName,
        @Param("fkName") String fkName,
        @Param("mainId") Object mainId
    );

    /**
     * 动态删除数据
     * @param tableName 表名
     * @param whereMap 条件Map
     * @return 影响行数
     */
    @DeleteProvider(type = DynamicSqlProvider.class, method = "buildDeleteSql")
    int dynamicDelete(@Param("tableName") String tableName,
                      @Param("where") Map<String, Object> whereMap);

    List<Map<String, Object>> selectSubTableByFk(String subTableName, String fkName, Long mainId);

    /**
     * 动态SQL构造器
     */
    class DynamicSqlProvider {
        public String buildDeleteSql(Map<String, Object> params) {
            String tableName = (String) params.get("tableName");
            Map<String, Object> where = (Map<String, Object>) params.get("where");

            StringBuilder sql = new StringBuilder("DELETE FROM ")
                .append(tableName);

            if (where != null && !where.isEmpty()) {
                sql.append(" WHERE ");
                int index = 0;
                for (Map.Entry<String, Object> entry : where.entrySet()) {
                    if (index++ > 0) {
                        sql.append(" AND ");
                    }
                    sql.append(entry.getKey())
                        .append(" = #{where.")
                        .append(entry.getKey())
                        .append("}");
                }
            }
            return sql.toString();
        }
    }

    @Delete("<script>" +
        "DELETE FROM ${tableName} WHERE " +
        "<foreach item='item' index='index' collection='whereKeys' open='(' separator=' OR ' close=')'>" +
        "  ${item} = #{where[item]}" +
        "</foreach>" +
        "</script>")
    int batchDelete(@Param("tableName") String tableName,
                    @Param("where") Map<String, Object> conditions,
                    @Param("whereKeys") List<String> whereKeys);

    @Select("<script>" +
        "SELECT * FROM ${tableName} " +
        "<where>${ew.sqlSegment}</where>" +
        "</script>")
    Page<Map<String, Object>> selectDynamicPage(
        Page<?> page,
        @Param("tableName") String tableName,
        @Param("ew") QueryWrapper<?> wrapper
    );

    @Select("<script>" +
        "SELECT * FROM ${tableName} " +
        "<where>${ew.sqlSegment}</where> " +
        "LIMIT 1" +
        "</script>")
    Map<String, Object> selectDynamicOne(
        @Param("tableName") String tableName,
        @Param("ew") QueryWrapper<?> wrapper
    );
}

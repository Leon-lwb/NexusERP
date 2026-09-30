package com.nexus.erp.prd.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;

/** 两个语句只能在调用方的同一 ERP 事务、同一连接中执行。 */
@Mapper
public interface PrdScheduleNoSequenceMapper {
    @Insert("INSERT INTO prd_schedule_no_sequence (tenant_id, plan_date, last_seq) "
        + "VALUES (#{tenantId}, #{planDate}, 1) "
        + "ON DUPLICATE KEY UPDATE last_seq = last_seq + 1")
    int reserveNext(@Param("tenantId") String tenantId, @Param("planDate") LocalDate planDate);

    @Select("SELECT last_seq FROM prd_schedule_no_sequence "
        + "WHERE tenant_id = #{tenantId} AND plan_date = #{planDate}")
    Integer currentSequence(@Param("tenantId") String tenantId, @Param("planDate") LocalDate planDate);
}

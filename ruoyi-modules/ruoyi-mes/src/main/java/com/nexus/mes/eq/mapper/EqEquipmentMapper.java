package com.nexus.mes.eq.mapper;

import com.nexus.common.mybatis.core.mapper.BaseMapperPlus;
import com.nexus.mes.eq.domain.EqEquipment;
import com.nexus.mes.eq.domain.vo.EqEquipmentVo;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * MES设备台账 Mapper接口
 *
 * @author Nexus
 */
public interface EqEquipmentMapper extends BaseMapperPlus<EqEquipment, EqEquipmentVo> {

    /**
     * 原子恢复设备 NORMAL: 仅当该设备无其他活跃报修(REPORTED/REPAIRING)且非 SCRAPPED 终态时才置 NORMAL。
     * <p>
     * 用途: 维修完成时联动恢复设备状态, 以单条 UPDATE + NOT EXISTS 消除
     * "查询活跃报修数 -> 更新设备状态"两步之间的 TOCTOU(完成瞬间插入新报修导致误恢复)。M3。
     * <p>
     * 租户隔离: equipment_id 为雪花全局唯一, 子查询按设备维度天然限定到单一租户的数据,
     * 即使租户插件未对子查询注入 tenant_id 结果亦正确; 外层 eq_equipment 由租户插件正常注入。
     * 状态取值与 {@link com.nexus.mes.eq.service.IEqEquipmentService#STATUS_NORMAL} 等常量一致。
     *
     * @param equipmentId 设备ID
     * @return 受影响行数(>0 表示实际恢复了 NORMAL)
     */
    @Update("UPDATE eq_equipment SET status = 'NORMAL' " +
        "WHERE equipment_id = #{equipmentId} " +
        "AND status <> 'SCRAPPED' " +
        "AND NOT EXISTS (SELECT 1 FROM eq_repair " +
        "WHERE equipment_id = #{equipmentId} AND status IN ('REPORTED', 'REPAIRING'))")
    int resetToNormalIfNoActiveRepair(@Param("equipmentId") Long equipmentId);

    /**
     * 结束保养: 仅当设备当前为 {@code MAINTENANCE} 时按既有状态机回到可运行态(MES-EQ-05)。
     * <p>
     * 目标状态由同一条 SQL 判定, 避免"先查活跃报修再更新"的 TOCTOU:
     * <ul>
     *   <li>该设备仍有活跃报修(REPORTED/REPAIRING) -> {@code FAULT}(故障未闭环);</li>
     *   <li>否则 -> {@code NORMAL}。</li>
     * </ul>
     * CAS 限定 {@code status = 'MAINTENANCE'}, 影响 0 行表示设备不在保养中(或并发已改), 不覆盖其他状态。
     * 租户隔离同 {@link #resetToNormalIfNoActiveRepair}: equipment_id 全局唯一, 子查询按设备维度天然限定。
     *
     * @param equipmentId 设备ID
     * @return 受影响行数(&gt;0 表示实际结束了保养)
     */
    @Update("UPDATE eq_equipment SET status = CASE WHEN EXISTS (" +
        "SELECT 1 FROM eq_repair WHERE equipment_id = #{equipmentId} " +
        "AND status IN ('REPORTED', 'REPAIRING')) THEN 'FAULT' ELSE 'NORMAL' END " +
        "WHERE equipment_id = #{equipmentId} AND status = 'MAINTENANCE'")
    int finishMaintenance(@Param("equipmentId") Long equipmentId);
}

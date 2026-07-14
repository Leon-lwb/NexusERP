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
}

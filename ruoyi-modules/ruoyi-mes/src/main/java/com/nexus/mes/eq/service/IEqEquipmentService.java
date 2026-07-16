package com.nexus.mes.eq.service;

import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.mes.eq.domain.bo.EqEquipmentBo;
import com.nexus.mes.eq.domain.bo.InspectionResultBo;
import com.nexus.mes.eq.domain.vo.EqEquipmentVo;
import com.nexus.mes.pro.domain.vo.ProWorkOrderVo;

import java.util.List;
import java.util.Map;

/**
 * MES设备台账 Service 业务层接口
 * <p>
 * 状态: NORMAL/MAINTENANCE/FAULT/SCRAPPED
 * 设备状态由维修记录联动(报修置FAULT, 完成恢复NORMAL), 也可手动维护
 *
 * @author Nexus
 */
public interface IEqEquipmentService {

    /** 状态: 正常 */
    String STATUS_NORMAL = "NORMAL";
    /** 状态: 保养中 */
    String STATUS_MAINTENANCE = "MAINTENANCE";
    /** 状态: 故障 */
    String STATUS_FAULT = "FAULT";
    /** 状态: 已报废 */
    String STATUS_SCRAPPED = "SCRAPPED";

    /**
     * 分页查询设备台账列表
     */
    TableDataInfo<EqEquipmentVo> selectPageList(EqEquipmentBo bo, PageQuery pageQuery);

    /**
     * 根据ID获取设备详情
     */
    EqEquipmentVo selectById(Long equipmentId);

    /**
     * 新增设备(默认状态 NORMAL)
     */
    EqEquipmentVo insertByBo(EqEquipmentBo bo);

    /**
     * 编辑设备
     */
    EqEquipmentVo updateByBo(EqEquipmentBo bo);

    /**
     * 批量删除设备
     */
    Boolean deleteByIds(Long[] ids);

    /**
     * 更新设备状态(供维修Service联动调用), 不改 SCRAPPED 终态
     *
     * @param equipmentId 设备ID
     * @param status      目标状态
     * @return 是否更新成功
     */
    Boolean updateStatus(Long equipmentId, String status);

    /**
     * 更新设备状态并携带变更原因, 状态实际变更时发布 MQ 事件
     *
     * @param equipmentId 设备ID
     * @param status      目标状态
     * @param reason      变更原因(可空)
     * @return 是否更新成功
     */
    Boolean updateStatus(Long equipmentId, String status, String reason);

    /**
     * 批量查询设备关联的 IoT 在线状态(消除前端逐行 N+1 HTTP 请求)
     *
     * @param equipmentIds 设备ID列表
     * @return equipmentId -> 在线状态(ONLINE/OFFLINE/UNKNOWN/UNBOUND)
     */
    Map<Long, String> batchIotStatus(List<Long> equipmentIds);

    /**
     * 不分页查询设备列表(导出用)
     */
    List<EqEquipmentVo> selectList(EqEquipmentBo bo);

    /**
     * 查询设备关联的 IoT 在线状态
     *
     * @param equipmentId MES 设备 ID
     * @return ONLINE / OFFLINE / UNKNOWN / UNBOUND
     */
    String getIotStatus(Long equipmentId);

    /**
     * 查询设备关联的 IoT 最新数据
     *
     * @param equipmentId MES 设备 ID
     * @return Map<propertyCode, value>，未关联返回空 Map
     */
    Map<String, String> getIotLatest(Long equipmentId);

    /**
     * 仅更新设备关联的 IoT 设备 ID（不影响其他字段）
     *
     * @param equipmentId MES 设备 ID
     * @param iotDeviceId IoT 设备 ID（可空，表示解除关联）
     * @return 是否更新成功
     */
    Boolean updateIotDeviceLink(Long equipmentId, Long iotDeviceId);

    /**
     * 查询设备当前关联的进行中工单(P1-6 设备-工单联动)
     *
     * @param equipmentId 设备ID
     * @return 进行中(RELEASED/IN_PROGRESS)的工单列表, 按创建时间倒序
     */
    List<ProWorkOrderVo> currentWorkOrders(Long equipmentId);

    /**
     * 设备状态计数汇总(监控大屏用, 全量 COUNT 不受分页 pageSize 限制)
     *
     * @return {total, normal, maintenance, fault, scrapped}
     */
    Map<String, Object> statusSummary();

    /**
     * 提交设备点检结果
     * <p>
     * 将点检结果写入 last_inspection JSON 字段; 若 overallPass=false 则
     * 将设备状态置为 FAULT 并触发 EQUIPMENT 类型安灯.
     *
     * @param equipmentId 设备ID
     * @param bo          点检结果
     * @return 是否成功
     */
    Boolean submitInspection(Long equipmentId, InspectionResultBo bo);

    /**
     * 维修完成时联动恢复设备 NORMAL: 原子操作, 仅当该设备无其他活跃报修(REPORTED/REPAIRING)
     * 且非 SCRAPPED 终态时才置 NORMAL。
     * <p>
     * 以单条 UPDATE + NOT EXISTS 消除"查询活跃报修数 -> 更新设备状态"两步之间的
     * TOCTOU(完成瞬间插入新报修导致误恢复)。状态实际变更时发布 MQ 事件(事务提交后)。
     *
     * @param equipmentId 设备ID
     * @return 是否实际恢复了 NORMAL(未恢复表示仍有活跃报修或已终态)
     */
    boolean resetToNormalIfNoActiveRepair(Long equipmentId);
}

package com.nexus.mes.eq.service;

import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.mes.eq.domain.bo.EqEquipmentBo;
import com.nexus.mes.eq.domain.bo.InspectionResultBo;
import com.nexus.mes.eq.domain.vo.EqEquipmentVo;

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
     * 更新设备状态(供维修Service联动调用)
     *
     * @param equipmentId 设备ID
     * @param status      目标状态
     * @return 是否更新成功
     */
    Boolean updateStatus(Long equipmentId, String status);

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
}

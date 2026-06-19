package com.nexus.mes.eq.service;

import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.mes.eq.domain.bo.EqEquipmentBo;
import com.nexus.mes.eq.domain.vo.EqEquipmentVo;

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
}

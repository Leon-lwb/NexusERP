package com.nexus.iot.firmware.service;

import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.iot.firmware.domain.IotFirmware;
import com.nexus.iot.firmware.domain.bo.IotFirmwareBo;

import java.util.List;

/**
 * IoT固件Service接口（OTA MVP）。
 *
 * @author nexus
 */
public interface IIotFirmwareService {

    /**
     * 分页查询固件列表。
     *
     * @param bo        查询条件（名称/产品型号/版本/状态）
     * @param pageQuery 分页参数
     * @return 固件分页结果
     */
    TableDataInfo<IotFirmware> selectPageList(IotFirmwareBo bo, PageQuery pageQuery);

    /**
     * 查询固件列表（不分页）。
     *
     * @param bo 查询条件
     * @return 固件列表
     */
    List<IotFirmware> selectList(IotFirmwareBo bo);

    /**
     * 查询固件详情。
     *
     * @param firmwareId 固件ID
     * @return 固件实体，不存在返回 null
     */
    IotFirmware selectById(Long firmwareId);

    /**
     * 登记固件（服务端强制 {@code status=DRAFT}，不接受客户端传入状态）。
     *
     * @param bo 固件业务对象
     * @return 影响行数
     */
    int insertByBo(IotFirmwareBo bo);

    /**
     * 修改固件（仅 DRAFT 可改；已发布固件版本号与固件包冻结）。
     *
     * @param bo 固件业务对象
     * @return 影响行数
     */
    int updateByBo(IotFirmwareBo bo);

    /**
     * 批量删除固件（已发布 RELEASED 固件拒绝删除，需先停用）。
     *
     * @param ids 固件ID数组
     * @return 删除影响行数
     */
    int deleteByIds(Long[] ids);

    /**
     * 变更发布状态（发布/停用），条件更新（CAS）。
     * <p>合法流转：DRAFT→RELEASED/DISABLED、RELEASED→DISABLED、DISABLED→RELEASED。
     * 影响 0 行（状态被并发变更）抛 {@code ServiceException}。
     *
     * @param firmwareId   固件ID
     * @param targetStatus 目标状态: RELEASED/DISABLED（DRAFT 不可回退）
     * @param remark       变更说明
     * @return 变更后的固件
     */
    IotFirmware changeStatus(Long firmwareId, String targetStatus, String remark);

    /**
     * 取"可用于下发"的固件（仅 RELEASED），供 OTA 任务服务调用。
     *
     * @param firmwareId 固件ID
     * @return 固件实体
     */
    IotFirmware requireReleased(Long firmwareId);
}

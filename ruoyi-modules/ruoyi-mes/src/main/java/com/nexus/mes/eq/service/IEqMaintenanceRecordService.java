package com.nexus.mes.eq.service;

import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.mes.eq.domain.bo.EqMaintenanceRecordBo;
import com.nexus.mes.eq.domain.vo.EqMaintenanceRecordVo;

import java.util.List;

/**
 * MES设备保养执行记录 Service 业务层接口
 * <p>
 * 保养执行记录可由保养计划到期完成时自动生成, 也可由人工手动录入。
 * 结果: NORMAL/ABNORMAL
 *
 * @author Nexus
 */
public interface IEqMaintenanceRecordService {

    /** 保养结果: 正常 */
    String RESULT_NORMAL = "NORMAL";
    /** 保养结果: 异常 */
    String RESULT_ABNORMAL = "ABNORMAL";

    /**
     * 分页查询保养执行记录列表
     *
     * @param bo 业务对象（含筛选条件/字段值）
     * @param pageQuery 分页参数（页码、每页大小、排序）
     * @return 分页结果
     */
    TableDataInfo<EqMaintenanceRecordVo> selectPageList(EqMaintenanceRecordBo bo, PageQuery pageQuery);

    /**
     * 查询保养执行记录列表(不分页, 用于导出)
     *
     * @param bo 业务对象（含筛选条件/字段值）
     * @return 结果列表
     */
    List<EqMaintenanceRecordVo> selectList(EqMaintenanceRecordBo bo);

    /**
     * 根据ID获取保养执行记录详情
     *
     * @param recordId 数据记录ID
     * @return 返回结果
     */
    EqMaintenanceRecordVo selectById(Long recordId);

    /**
     * 查询某设备的保养执行历史(用于设备详情页)
     *
     * @param equipmentId 设备ID
     * @return 保养记录列表(按执行时间倒序)
     */
    List<EqMaintenanceRecordVo> selectByEquipment(Long equipmentId);

    /**
     * 手动新增保养执行记录(自动填充执行人为当前用户)
     *
     * @param bo 业务对象（含筛选条件/字段值）
     * @return 返回结果
     */
    EqMaintenanceRecordVo insertByBo(EqMaintenanceRecordBo bo);

    /**
     * 批量删除保养执行记录
     *
     * @param ids ID数组
     * @return 是否成功
     */
    Boolean deleteByIds(Long[] ids);
}

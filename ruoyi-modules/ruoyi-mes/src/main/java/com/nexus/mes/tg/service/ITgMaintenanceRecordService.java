package com.nexus.mes.tg.service;

import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.mes.tg.domain.bo.TgMaintenanceRecordBo;
import com.nexus.mes.tg.domain.vo.TgMaintenanceRecordVo;

/**
 * MES工装保养记录 Service 业务层接口
 * <p>注: 保养记录的"归零+状态机"逻辑由 TgToolingService.completeMaintain 承载;
 * 本接口仅做记录的 CRUD,insertByBo 不触发工装状态变更。</p>
 *
 * @author Nexus
 */
public interface ITgMaintenanceRecordService {

    /**
     * 分页查询保养记录列表
     */
    TableDataInfo<TgMaintenanceRecordVo> selectPageList(TgMaintenanceRecordBo bo, PageQuery pageQuery);

    /**
     * 根据ID获取保养记录详情
     */
    TgMaintenanceRecordVo selectById(Long recordId);

    /**
     * 新增保养记录(纯记录,不触发工装归零;归零走 TgToolingService.completeMaintain)
     */
    Boolean insertByBo(TgMaintenanceRecordBo bo);

    /**
     * 修改保养记录
     */
    Boolean updateByBo(TgMaintenanceRecordBo bo);

    /**
     * 批量删除保养记录
     */
    Boolean deleteByIds(Long[] ids);
}

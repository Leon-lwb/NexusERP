package com.nexus.mes.tg.service;

import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.mes.tg.domain.bo.TgUsageRecordBo;
import com.nexus.mes.tg.domain.vo.TgUsageRecordVo;

/**
 * MES工装使用/领用记录 Service 业务层接口
 * <p>注: 领用/归还的状态机逻辑由 TgToolingService.checkout/doReturn 承载;
 * 本接口仅做记录的 CRUD。</p>
 *
 * @author Nexus
 */
public interface ITgUsageRecordService {

    /**
     * 分页查询使用记录列表
     */
    TableDataInfo<TgUsageRecordVo> selectPageList(TgUsageRecordBo bo, PageQuery pageQuery);

    /**
     * 根据ID获取使用记录详情
     */
    TgUsageRecordVo selectById(Long usageId);

    /**
     * 新增使用记录(纯记录,不触发工装状态变更)
     */
    Boolean insertByBo(TgUsageRecordBo bo);

    /**
     * 修改使用记录
     */
    Boolean updateByBo(TgUsageRecordBo bo);

    /**
     * 批量删除使用记录
     */
    Boolean deleteByIds(Long[] ids);
}

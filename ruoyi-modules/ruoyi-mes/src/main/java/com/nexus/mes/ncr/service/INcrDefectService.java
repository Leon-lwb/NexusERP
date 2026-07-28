package com.nexus.mes.ncr.service;

import com.nexus.mes.ncr.domain.NcrDefect;
import com.nexus.mes.ncr.domain.bo.NcrDefectBo;
import com.nexus.mes.ncr.domain.vo.NcrDefectVo;

import java.util.List;

/**
 * MES NCR缺陷明细 Service 业务层接口
 * <p>
 * 管理 NCR 单下的缺陷明细记录,支持批量创建和按 NCR 查询。
 * </p>
 *
 * @author Nexus
 */
public interface INcrDefectService {

    /**
     * 查询缺陷明细列表(按 NCR 单ID)
     *
     * @param ncrId NCR单ID
     * @return 缺陷明细列表
     */
    List<NcrDefectVo> selectByNcrId(Long ncrId);

    /**
     * 新增缺陷明细(补录)
     *
     * @param bo 缺陷明细业务对象
     * @return 新增成功返回 true
     */
    Boolean insertByBo(NcrDefectBo bo);

    /**
     * 批量插入缺陷明细(供 NcrOrderServiceImpl.createNcr 内部调用)
     *
     * @param ncrId   NCR单ID
     * @param defects 缺陷明细列表
     */
    void batchInsertForNcr(Long ncrId, List<NcrDefect> defects);
}
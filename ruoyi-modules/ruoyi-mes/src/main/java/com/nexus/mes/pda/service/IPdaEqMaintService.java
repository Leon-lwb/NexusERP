package com.nexus.mes.pda.service;

import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.mes.pda.domain.bo.PdaMaintExecuteBo;
import com.nexus.mes.pda.domain.vo.MesActionResultVo;
import com.nexus.mes.pda.domain.vo.PdaEqMaintDetailVo;
import com.nexus.mes.pda.domain.vo.PdaEqMaintItemVo;

/**
 * PDA 保养维保 Service
 * <p>
 * 到期保养计划在 PDA 查看并执行: 执行落保养记录(eq_maintenance_record)并推进计划下次保养日期。
 * 计划维护(建计划/停用)仍在 PC 端完成。
 */
public interface IPdaEqMaintService {

    /**
     * 到期保养待办 (ACTIVE 且 nextDate<=今天, keyword 扫设备编码/名称或计划名)
     */
    TableDataInfo<PdaEqMaintItemVo> todo(String keyword, PageQuery pageQuery);

    /**
     * 保养计划详情 (计划+设备+最近保养记录)
     */
    PdaEqMaintDetailVo detail(Long planId);

    /**
     * 执行保养: 落保养记录 + 到期则推进下次保养日期
     */
    MesActionResultVo execute(Long planId, PdaMaintExecuteBo bo);
}

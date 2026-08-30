package com.nexus.erp.wm.service;

import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.erp.wm.domain.bo.pda.PdaLineConfirmBo;
import com.nexus.erp.wm.domain.bo.pda.PdaShelfBo;
import com.nexus.erp.wm.domain.vo.pda.PdaActionResultVo;
import com.nexus.erp.wm.domain.vo.pda.PdaReceiveDetailVo;
import com.nexus.erp.wm.domain.vo.pda.PdaTodoItemVo;

/**
 * PDA 收货作业服务
 * <p>
 * 编排既有收货域服务, 对齐真实状态机:
 * 行级核销(累计实收+写上架明细) → 上架确认(APPROVED→SHELVED) → 执行收货过账(SHELVED→FINISHED)
 */
public interface IPdaReceiveService {

    /** 待收货单据列表(扫码/单号定位) */
    TableDataInfo<PdaTodoItemVo> todo(String keyword, PageQuery pageQuery);

    /** 收货单详情(头+行) */
    PdaReceiveDetailVo detail(Long receiveId);

    /** 行级扫码核销: 累计实收数量, 写入/更新上架明细 */
    PdaActionResultVo confirmLine(Long receiveId, PdaLineConfirmBo bo);

    /** 上架确认: 库位填充未定位明细后整单上架 (APPROVED→SHELVED) */
    PdaActionResultVo shelf(Long receiveId, PdaShelfBo bo);

    /** 执行收货过账 (SHELVED→FINISHED) */
    PdaActionResultVo finish(Long receiveId);
}

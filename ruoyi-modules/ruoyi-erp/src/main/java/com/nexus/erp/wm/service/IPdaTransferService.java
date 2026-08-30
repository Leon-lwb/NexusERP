package com.nexus.erp.wm.service;

import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.erp.wm.domain.vo.pda.PdaActionResultVo;
import com.nexus.erp.wm.domain.vo.pda.PdaTodoItemVo;
import com.nexus.erp.wm.domain.vo.pda.PdaTransferDetailVo;

/**
 * PDA 移库作业服务 (M1 整单执行; 两段状态机为 M2)
 */
public interface IPdaTransferService {

    TableDataInfo<PdaTodoItemVo> todo(String keyword, PageQuery pageQuery);

    PdaTransferDetailVo detail(Long transferId);

    /** 整单执行移库 (仅已审批单据) */
    PdaActionResultVo execute(Long transferId);
}

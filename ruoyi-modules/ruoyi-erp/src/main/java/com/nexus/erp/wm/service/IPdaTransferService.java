package com.nexus.erp.wm.service;

import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.erp.wm.domain.bo.pda.PdaShelfBo;
import com.nexus.erp.wm.domain.bo.pda.PdaTransferTakeoffBo;
import com.nexus.erp.wm.domain.vo.pda.PdaActionResultVo;
import com.nexus.erp.wm.domain.vo.pda.PdaTodoItemVo;
import com.nexus.erp.wm.domain.vo.pda.PdaTransferDetailVo;

/**
 * PDA 移库作业服务 (M2 两段式: 行级下架核销 → 扫目标库位上架过账)
 */
public interface IPdaTransferService {

    TableDataInfo<PdaTodoItemVo> todo(String keyword, PageQuery pageQuery);

    PdaTransferDetailVo detail(Long transferId);

    /** 行级下架核销: 校验源库位一致后标记该行已下架; 全部行下架后单头转 TAKEN_OFF */
    PdaActionResultVo takeoff(Long transferId, PdaTransferTakeoffBo bo);

    /** 上架确认: 校验目标库位后执行整单过账 (仅 TAKEN_OFF 单据) */
    PdaActionResultVo shelf(Long transferId, PdaShelfBo bo);

    /** 整单执行移库 (仅已审批单据, 兜底直通模式) */
    PdaActionResultVo execute(Long transferId);
}

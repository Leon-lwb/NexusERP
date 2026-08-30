package com.nexus.mes.pda.service;

import com.nexus.mes.pda.domain.bo.PdaSnBindBo;
import com.nexus.mes.pda.domain.bo.PdaSnUnbindBo;
import com.nexus.mes.pda.domain.vo.MesActionResultVo;
import com.nexus.mes.pda.domain.vo.PdaSnBindingVo;

/**
 * PDA SN 绑定/解绑作业服务
 */
public interface IPdaSnService {

    /** 按 SN 查询绑定; 未绑定返回 null */
    PdaSnBindingVo query(String snCode);

    /** 绑定: SN -> 批次 (物料随批次带出); 重复绑定拒绝 */
    MesActionResultVo bind(PdaSnBindBo bo);

    /** 解绑: 按 SN 移除绑定; 未绑定拒绝 */
    MesActionResultVo unbind(PdaSnUnbindBo bo);
}

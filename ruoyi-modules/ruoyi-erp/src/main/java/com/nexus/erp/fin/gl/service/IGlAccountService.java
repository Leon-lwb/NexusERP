package com.nexus.erp.fin.gl.service;

import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.erp.fin.gl.domain.bo.GlAccountBo;
import com.nexus.erp.fin.gl.domain.vo.GlAccountVo;

import java.util.List;

/**
 * 会计科目 Service
 *
 * @author nexus
 * @date 2026-07-29
 */
public interface IGlAccountService {

    List<GlAccountVo> queryList(GlAccountBo bo);

    GlAccountVo queryById(Long accountId);

    Boolean insertByBo(GlAccountBo bo);

    Boolean updateByBo(GlAccountBo bo);

    Boolean deleteByIds(Long[] accountIds);

    /** 末级科目(凭证录入下拉) */
    List<GlAccountVo> selectLeafAccounts(String accountType);

    /** 校验编码唯一 */
    Boolean checkAccountCodeUnique(GlAccountBo bo);
}

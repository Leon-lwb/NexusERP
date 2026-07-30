package com.nexus.erp.fin.gl.service;

import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.erp.fin.gl.domain.bo.GlVoucherBo;
import com.nexus.erp.fin.gl.domain.vo.GlVoucherVo;

import java.util.List;

/**
 * 记账凭证 Service(凭证引擎)
 *
 * @author nexus
 * @date 2026-07-29
 */
public interface IGlVoucherService {

    TableDataInfo<GlVoucherVo> queryPageList(GlVoucherBo bo, PageQuery pageQuery);

    List<GlVoucherVo> queryList(GlVoucherBo bo);

    GlVoucherVo queryById(Long voucherId);

    Boolean insertByBo(GlVoucherBo bo);

    Boolean updateByBo(GlVoucherBo bo);

    Boolean deleteByIds(Long[] voucherIds);

    /** 过账:校验借贷平衡+期间开放+末级科目,写科目余额 */
    void post(Long voucherId);

    /** 反过账:期间未锁定,回滚科目余额 */
    void unpost(Long voucherId);

    /** 作废(DRAFT/POSTED -> CANCELLED;POSTED 先回滚余额) */
    void cancel(Long voucherId);

    /** 业务自动制单:构建+过账(供 AccountingPoster 调用,幂等按 source+sourceId 去重) */
    GlVoucherVo createAndPost(VoucherBuildRequest req);
}

package com.nexus.erp.fin.gl.service;

import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.erp.fin.gl.domain.GlPeriod;
import com.nexus.erp.fin.gl.domain.bo.GlPeriodBo;
import com.nexus.erp.fin.gl.domain.vo.GlPeriodVo;

import java.util.Date;

/**
 * 会计期间 Service
 *
 * @author nexus
 * @date 2026-07-29
 */
public interface IGlPeriodService {

    TableDataInfo<GlPeriodVo> queryPageList(GlPeriodBo bo, PageQuery pageQuery);

    GlPeriodVo queryById(Long periodId);

    /**
     * 按凭证日期取得期间(不存在则自动派生一个 OPEN 期间)
     */
    GlPeriod getOrDerivePeriod(Date voucherDate);

    /**
     * 关账(OPEN->CLOSED,校验该期间无草稿凭证)
     */
    Boolean closePeriod(Long periodId);

    /**
     * 锁定(CLOSED->LOCKED,不可逆)
     */
    Boolean lockPeriod(Long periodId);

    /**
     * 反结账(CLOSED->OPEN)
     */
    Boolean reopenPeriod(Long periodId);

    /**
     * 批量生成某会计年度 12 个期间
     */
    Boolean generateYearPeriods(Integer fiscalYear);
}

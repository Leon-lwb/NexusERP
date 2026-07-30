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

    /**
     * 分页查询会计期间
     *
     * @param bo         查询条件(会计年度/期间号/状态)
     * @param pageQuery  分页参数
     * @return 期间分页数据
     */
    TableDataInfo<GlPeriodVo> queryPageList(GlPeriodBo bo, PageQuery pageQuery);

    /**
     * 根据主键查询会计期间
     *
     * @param periodId 期间ID
     * @return 期间VO
     */
    GlPeriodVo queryById(Long periodId);

    /**
     * 按凭证日期取得期间(不存在则自动派生一个 OPEN 期间)
     * <p>根据日期计算 fiscalYear 和 periodNo 查询;若不存在则自动创建并返回 OPEN 状态期间。</p>
     *
     * @param voucherDate 凭证日期
     * @return 会计期间实体(含持久化 ID)
     */
    GlPeriod getOrDerivePeriod(Date voucherDate);

    /**
     * 关账(OPEN->CLOSED,校验该期间无草稿凭证)
     * <p>仅 OPEN 状态可关账;校验该期间无草稿凭证;乐观锁防并发。</p>
     *
     * @param periodId 期间ID
     * @return true
     * @throws ServiceException 期间不存在/非 OPEN/存在草稿凭证/乐观锁冲突
     */
    Boolean closePeriod(Long periodId);

    /**
     * 锁定(CLOSED->LOCKED,不可逆)
     * <p>仅 CLOSED 状态可锁定;锁定后不可反结账,不可反过账。</p>
     *
     * @param periodId 期间ID
     * @return true
     * @throws ServiceException 期间不存在/非 CLOSED/乐观锁冲突
     */
    Boolean lockPeriod(Long periodId);

    /**
     * 反结账(CLOSED->OPEN)
     * <p>仅 CLOSED 状态可反结账,清空关账人和关账时间。</p>
     *
     * @param periodId 期间ID
     * @return true
     * @throws ServiceException 期间不存在/非 CLOSED/乐观锁冲突
     */
    Boolean reopenPeriod(Long periodId);

    /**
     * 批量生成某会计年度 12 个期间
     * <p>逐月检查是否存在,已存在则跳过。新期间均为 OPEN 状态。</p>
     *
     * @param fiscalYear 会计年度
     * @return true
     */
    Boolean generateYearPeriods(Integer fiscalYear);
}

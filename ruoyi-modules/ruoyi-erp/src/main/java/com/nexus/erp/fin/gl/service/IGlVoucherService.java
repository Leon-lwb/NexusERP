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

    /**
     * 分页查询记账凭证(含明细行)
     *
     * @param bo         查询条件(凭证号/状态/来源/期间/日期范围)
     * @param pageQuery  分页参数
     * @return 凭证分页数据
     */
    TableDataInfo<GlVoucherVo> queryPageList(GlVoucherBo bo, PageQuery pageQuery);

    /**
     * 查询记账凭证列表
     *
     * @param bo 查询条件(凭证号/状态/来源/期间/日期范围)
     * @return 凭证列表
     */
    List<GlVoucherVo> queryList(GlVoucherBo bo);

    /**
     * 根据主键查询记账凭证(含明细行)
     *
     * @param voucherId 凭证ID
     * @return 凭证VO(含 voucherLineList)
     */
    GlVoucherVo queryById(Long voucherId);

    /**
     * 新增记账凭证(草稿):校验借贷平衡 + 末级科目,自动生成凭证号
     * <p>默认凭证字"J",来源"manual",币种"CNY",状态 DRAFT,version=0。</p>
     *
     * @param bo 凭证业务对象(含明细行)
     * @return 成功/失败
     * @throws ServiceException 借贷不平衡/分录行空/科目非末级
     */
    Boolean insertByBo(GlVoucherBo bo);

    /**
     * 修改记账凭证:仅草稿可修改,差量更新明细行,乐观锁防并发
     *
     * @param bo 凭证业务对象(含明细行)
     * @return 成功/失败
     * @throws ServiceException 凭证不存在/非草稿/乐观锁冲突/借贷不平衡
     */
    Boolean updateByBo(GlVoucherBo bo);

    /**
     * 删除记账凭证:仅草稿可删除,级联删除明细行
     *
     * @param voucherIds 凭证ID数组
     * @return 成功/失败
     * @throws ServiceException 存在非草稿凭证
     */
    Boolean deleteByIds(Long[] voucherIds);

    /**
     * 过账:校验借贷平衡 + 期间开放 + 末级科目,乐观锁改状态,累加科目余额
     * <p>DRAFT -> POSTED;自动推导期间;按版本号乐观锁防并发。</p>
     *
     * @param voucherId 凭证ID
     * @throws ServiceException 凭证不存在/非草稿/期间非开放/乐观锁冲突
     */
    void post(Long voucherId);

    /**
     * 反过账:期间未锁定,回滚科目余额,乐观锁恢复草稿状态
     * <p>POSTED -> DRAFT;LOCKED 期间不可反过账。</p>
     *
     * @param voucherId 凭证ID
     * @throws ServiceException 凭证不存在/非已过账/期间已锁定/乐观锁冲突
     */
    void unpost(Long voucherId);

    /**
     * 作废:POSTED 先回滚余额再置 CANCELLED;DRAFT 直接置 CANCELLED
     * <p>DRAFT/POSTED -> CANCELLED;POSTED 时 LOCKED 期间不可作废。</p>
     *
     * @param voucherId 凭证ID
     * @throws ServiceException 凭证不存在/状态非法/期间已锁定/乐观锁冲突
     */
    void cancel(Long voucherId);

    /**
     * 业务自动制单:构建+直过账(供 AccountingPoster SPI 调用)
     * <p>幂等:同 voucherSource+sourceId 已制单则返回既有;校验借贷平衡+末级科目+期间开放;写入科目余额。</p>
     *
     * @param req 制单请求(含来源、分录行、摘要等信息)
     * @return 已过账凭证VO(新建或既有)
     * @throws ServiceException 借贷不平衡/期间非开放/科目非末级
     */
    GlVoucherVo createAndPost(VoucherBuildRequest req);
}

package com.nexus.mes.ncr.service;

import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.mes.ncr.domain.bo.NcrReworkOrderBo;
import com.nexus.mes.ncr.domain.vo.NcrReworkOrderVo;

import java.math.BigDecimal;

/**
 * MES NCR返工单 Service 业务层接口
 * <p>
 * 返工单由 NCR 处置(REWORK)自动派生,关联返工工单。
 * 状态: OPEN -> IN_PROGRESS -> COMPLETED
 * </p>
 *
 * @author Nexus
 */
public interface INcrReworkOrderService {

    /** 状态: 待返工 */
    String STATUS_OPEN = "OPEN";
    /** 状态: 进行中 */
    String STATUS_IN_PROGRESS = "IN_PROGRESS";
    /** 状态: 已完成 */
    String STATUS_COMPLETED = "COMPLETED";

    /**
     * 分页查询返工单列表
     *
     * @param bo        查询条件
     * @param pageQuery 分页参数
     * @return 分页结果
     */
    TableDataInfo<NcrReworkOrderVo> selectPageList(NcrReworkOrderBo bo, PageQuery pageQuery);

    /**
     * 根据ID获取返工单详情
     *
     * @param reworkId 返工记录ID
     * @return 返工单视图对象
     */
    NcrReworkOrderVo selectById(Long reworkId);

    /**
     * 批量删除返工单
     *
     * @param ids 返工记录ID数组
     * @return 删除成功返回 true
     */
    Boolean deleteByIds(Long[] ids);

    /**
     * 派生返工工单
     * <p>由 NcrOrderServiceImpl.dispose 在处置方式为 REWORK 时调用。
     * 自动创建返工工单(复制原工单物料/工艺/车间),并记录返工关联。
     * 注意: 本方法不修改 NcrOrder 状态,由调用方 NcrOrderServiceImpl 负责 CAS 流转。</p>
     *
     * @param ncrId        NCR单ID
     * @param originalWoId 原始工单ID
     * @param reworkQty    返工数量
     * @param reason       返工原因
     * @return 返工单视图对象
     */
    NcrReworkOrderVo deriveReworkOrder(Long ncrId, Long originalWoId, BigDecimal reworkQty, String reason);
}
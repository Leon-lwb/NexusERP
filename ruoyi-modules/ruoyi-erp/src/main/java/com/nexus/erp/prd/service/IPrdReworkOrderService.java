package com.nexus.erp.prd.service;

import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.erp.prd.domain.PrdReworkOrder;
import com.nexus.erp.prd.domain.bo.PrdReworkOrderBo;
import com.nexus.erp.prd.domain.vo.PrdReworkOrderVo;

import java.util.List;

/**
 * 返工订单 Service 接口
 * <p>
 * 定义返工订单相关的业务逻辑操作，包括返工订单的查询、新增、修改、删除及状态流转等。
 *
 * @author nexus
 */
public interface IPrdReworkOrderService {

    /** 状态：草稿 */
    String STATUS_DRAFT = "DRAFT";
    /** 状态：进行中 */
    String STATUS_IN_PROGRESS = "IN_PROGRESS";
    /** 状态：已完成 */
    String STATUS_COMPLETED = "COMPLETED";

    /**
     * 分页查询返工订单列表
     *
     * @param bo        查询参数，包含返工单号、来源工单、物料等筛选条件
     * @param pageQuery 分页参数，包含页码、每页条数及排序信息
     * @return 返工订单分页列表
     */
    TableDataInfo<PrdReworkOrderVo> selectPageList(PrdReworkOrderBo bo, PageQuery pageQuery);

    /**
     * 查询返工订单列表（不分页）
     *
     * @param bo 查询参数，包含返工单号、来源工单、物料等筛选条件
     * @return 返工订单列表
     */
    List<PrdReworkOrderVo> selectList(PrdReworkOrderBo bo);

    /**
     * 根据 ID 查询返工订单详情
     *
     * @param id 返工订单 ID
     * @return 返工订单详细信息
     */
    PrdReworkOrderVo selectById(Long id);

    /**
     * 新增返工订单
     *
     * @param bo 返工订单业务对象，包含来源工单、物料、返工数量等信息
     * @return 影响的行数
     */
    int insertByBo(PrdReworkOrderBo bo);

    /**
     * 修改返工订单
     *
     * @param bo 返工订单业务对象，包含需要更新的字段信息
     * @return 影响的行数
     */
    int updateByBo(PrdReworkOrderBo bo);

    /**
     * 批量删除返工订单
     *
     * @param ids 要删除的返工订单 ID 数组
     * @return 影响的行数
     */
    int deleteByIds(Long[] ids);

    /**
     * 开始返工订单
     * <p>
     * 将返工订单状态从 DRAFT 更新为 IN_PROGRESS（进行中）。
     *
     * @param reworkId 返工订单 ID
     */
    void start(Long reworkId);

    /**
     * 完成返工订单
     * <p>
     * 将返工订单状态从 IN_PROGRESS 更新为 COMPLETED（已完成），
     * 并将报废数量回写到来源工单。
     *
     * @param reworkId 返工订单 ID
     */
    void complete(Long reworkId);

    /**
     * 返工订单出库（领料）
     * <p>
     * 校验返工订单状态为进行中，调用库存过账服务执行返工出库操作。
     *
     * @param reworkId 返工订单 ID
     */
    void issueMaterial(Long reworkId);

    /**
     * 返工订单入库（收货）
     * <p>
     * 校验返工订单状态为进行中，调用库存过账服务执行返工入库操作。
     *
     * @param reworkId 返工订单 ID
     */
    void receiveMaterial(Long reworkId);

    /**
     * 根据 ID 查询返工订单实体（用于内部消费者等场景，不经过 VO 转换）
     *
     * @param id 返工订单 ID
     * @return 返工订单实体，未找到返回 null
     */
    PrdReworkOrder getById(Long id);
}

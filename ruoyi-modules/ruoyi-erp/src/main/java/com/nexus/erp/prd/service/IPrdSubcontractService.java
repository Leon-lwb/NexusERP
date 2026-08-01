package com.nexus.erp.prd.service;

import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.erp.prd.domain.bo.PrdSubcontractBo;
import com.nexus.erp.prd.domain.vo.PrdSubcontractVo;

import java.util.List;
import java.util.Map;

/**
 * 委外订单 Service 接口
 * <p>
 * 定义委外订单相关的业务逻辑操作，包括委外订单的 CRUD、状态流转（下达、开始、完工、关闭）等。
 *
 * @author nexus
 */
public interface IPrdSubcontractService {

    /** 状态：草稿 */
    String STATUS_DRAFT = "DRAFT";
    /** 状态：已提交 */
    String STATUS_SUBMITTED = "SUBMITTED";
    /** 状态：已批准 */
    String STATUS_APPROVED = "APPROVED";
    /** 状态：已下达 */
    String STATUS_RELEASED = "RELEASED";
    /** 状态：进行中 */
    String STATUS_IN_PROGRESS = "IN_PROGRESS";
    /** 状态：已完成 */
    String STATUS_COMPLETED = "COMPLETED";
    /** 状态：已关闭 */
    String STATUS_CLOSED = "CLOSED";

    /**
     * 分页查询委外订单列表
     *
     * @param bo        查询参数，包含委外单号、供应商、物料等筛选条件
     * @param pageQuery 分页参数，包含页码、每页条数及排序信息
     * @return 委外订单分页列表
     */
    TableDataInfo<PrdSubcontractVo> selectPageList(PrdSubcontractBo bo, PageQuery pageQuery);

    /**
     * 查询委外订单列表（不分页）
     *
     * @param bo 查询参数，包含委外单号、供应商、物料等筛选条件
     * @return 委外订单列表
     */
    List<PrdSubcontractVo> selectList(PrdSubcontractBo bo);

    /**
     * 根据 ID 查询委外订单详情
     *
     * @param id 委外订单 ID
     * @return 委外订单详细信息
     */
    PrdSubcontractVo selectById(Long id);

    /**
     * 新增委外订单
     *
     * @param bo 委外订单业务对象，包含供应商、物料、数量、价格等信息
     * @return 影响的行数
     */
    int insertByBo(PrdSubcontractBo bo);

    /**
     * 修改委外订单
     *
     * @param bo 委外订单业务对象，包含需要更新的字段信息
     * @return 影响的行数
     */
    int updateByBo(PrdSubcontractBo bo);

    /**
     * 批量删除委外订单
     *
     * @param ids 要删除的委外订单 ID 数组
     * @return 影响的行数
     */
    int deleteByIds(Long[] ids);

    /**
     * 下达委外订单
     * <p>
     * 将委外订单状态更新为已下达，并自动生成关联生产工单。
     *
     * @param subcontractId 委外订单 ID
     */
    void release(Long subcontractId);

    /**
     * 开始委外订单
     * <p>
     * 将委外订单状态更新为进行中，记录实际开始时间。
     *
     * @param subcontractId 委外订单 ID
     */
    void start(Long subcontractId);

    /**
     * 完工委外订单
     * <p>
     * 将委外订单状态更新为已完成，记录实际结束时间。
     *
     * @param subcontractId 委外订单 ID
     */
    void complete(Long subcontractId);

    /**
     * 关闭委外订单
     * <p>
     * 将委外订单状态更新为已关闭。
     *
     * @param subcontractId 委外订单 ID
     */
    void close(Long subcontractId);

    /**
     * 提交审批委外订单
     * <p>
     * 将草稿状态更新为已提交。
     *
     * @param subcontractId 委外订单 ID
     */
    void submit(Long subcontractId);

    /**
     * 查询委外订单操作日志（状态时间线）
     *
     * @param subcontractId 委外订单 ID
     * @return 操作日志列表
     */
    List<Map<String, Object>> getOperationLogs(Long subcontractId);
}

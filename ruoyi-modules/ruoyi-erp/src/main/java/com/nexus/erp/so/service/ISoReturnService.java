package com.nexus.erp.so.service;

import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.erp.so.domain.bo.SoReturnBo;
import com.nexus.erp.so.domain.vo.SoReturnVo;
import com.nexus.workflow.service.WfCallBackServiceI;

import java.util.List;

/**
 * 销售退货Service接口
 *
 * @author nexus
 */
public interface ISoReturnService extends WfCallBackServiceI {

    /** 草稿状态 */
    String STATUS_DRAFT = "DRAFT";
    /** 审批中状态 */
    String STATUS_APPROVING = "APPROVING";
    /** 已确认状态 */
    String STATUS_CONFIRMED = "CONFIRMED";
    /** 已收货状态 */
    String STATUS_RECEIVED = "RECEIVED";
    /** 已关闭状态 */
    String STATUS_CLOSED = "CLOSED";

    /**
     * 分页查询销售退货列表
     *
     * @param bo        查询参数
     * @param pageQuery 分页参数
     * @return 销售退货分页列表
     */
    TableDataInfo<SoReturnVo> selectPageList(SoReturnBo bo, PageQuery pageQuery);

    /**
     * 查询销售退货列表
     *
     * @param bo 查询参数
     * @return 销售退货列表
     */
    List<SoReturnVo> selectList(SoReturnBo bo);

    /**
     * 根据ID查询销售退货详情
     *
     * @param id 销售退货ID
     * @return 销售退货详情
     */
    SoReturnVo selectById(Long id);

    /**
     * 新增销售退货
     *
     * @param bo 销售退货业务对象
     * @return 影响行数
     */
    int insertByBo(SoReturnBo bo);

    /**
     * 修改销售退货
     *
     * @param bo 销售退货业务对象
     * @return 影响行数
     */
    int updateByBo(SoReturnBo bo);

    /**
     * 批量删除销售退货
     *
     * @param ids 销售退货ID数组
     * @return 影响行数
     */
    int deleteByIds(Long[] ids);

    /**
     * 确认销售退货单
     *
     * @param returnId 销售退货ID
     */
    void confirm(Long returnId);

    /**
     * 执行退货入库（扣减库存并生成库存交易记录）
     *
     * @param returnId 销售退货ID
     */
    void execute(Long returnId);

    /**
     * 发起审批流程
     *
     * @param returnIdStr  销售退货ID（字符串）
     * @param selectFlowId 流程定义ID
     * @return 是否成功
     */
    boolean submit(String returnIdStr, String selectFlowId);

    /**
     * 获取流程实例ID
     *
     * @param returnIdStr 销售退货ID（字符串）
     * @return 流程实例ID，无则返回null
     */
    String getProcInsId(String returnIdStr);
}

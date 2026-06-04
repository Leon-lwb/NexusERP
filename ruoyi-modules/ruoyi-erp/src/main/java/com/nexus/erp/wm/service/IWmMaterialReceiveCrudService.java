package com.nexus.erp.wm.service;

import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.erp.wm.domain.bo.WmMaterialReceiveBo;
import com.nexus.erp.wm.domain.vo.WmMaterialReceiveVo;
import com.nexus.workflow.service.WfCallBackServiceI;

import java.util.List;

/**
 * 物料入库单CRUD Service接口
 *
 * @author nexus
 */
public interface IWmMaterialReceiveCrudService extends WfCallBackServiceI {

    /** 草稿状态 */
    String STATUS_DRAFT = "DRAFT";
    /** 审批中状态 */
    String STATUS_APPROVING = "APPROVING";
    /** 待上架状态（审批通过） */
    String STATUS_APPROVED = "APPROVED";
    /** 待入库状态（已上架） */
    String STATUS_SHELVED = "SHELVED";
    /** 已完成状态 */
    String STATUS_FINISHED = "FINISHED";
    /** 已驳回状态 */
    String STATUS_REJECTED = "REJECTED";
    /** 已取消状态 */
    String STATUS_CANCELED = "CANCELED";

    /**
     * 分页查询物料入库单列表
     *
     * @param bo         查询条件
     * @param pageQuery  分页参数
     * @return 分页结果
     */
    TableDataInfo<WmMaterialReceiveVo> selectPageList(WmMaterialReceiveBo bo, PageQuery pageQuery);

    /**
     * 查询物料入库单列表
     *
     * @param bo 查询条件
     * @return 入库单列表
     */
    List<WmMaterialReceiveVo> selectList(WmMaterialReceiveBo bo);

    /**
     * 根据ID查询物料入库单（含行和明细）
     *
     * @param id 入库单ID
     * @return 入库单详情
     */
    WmMaterialReceiveVo selectById(Long id);

    /**
     * 新增物料入库单
     *
     * @param bo 入库单业务对象
     * @return 影响行数
     */
    int insertByBo(WmMaterialReceiveBo bo);

    /**
     * 修改物料入库单
     *
     * @param bo 入库单业务对象
     * @return 影响行数
     */
    int updateByBo(WmMaterialReceiveBo bo);

    /**
     * 批量删除物料入库单
     *
     * @param ids 入库单ID数组
     * @return 影响行数
     */
    int deleteByIds(Long[] ids);

    /**
     * 带校验的更新物料入库单
     *
     * @param bo 入库单业务对象
     * @return 是否成功
     */
    boolean updateWithValidation(WmMaterialReceiveBo bo);

    /**
     * 提交物料入库单审批
     *
     * @param receiveIdStr  入库单ID字符串
     * @param selectFlowId  审批流程ID
     * @return 是否成功
     */
    boolean submit(String receiveIdStr, String selectFlowId);

    /**
     * 获取入库单流程实例ID
     *
     * @param receiveIdStr 入库单ID字符串
     * @return 流程实例ID
     */
    String getProcInsId(String receiveIdStr);
}

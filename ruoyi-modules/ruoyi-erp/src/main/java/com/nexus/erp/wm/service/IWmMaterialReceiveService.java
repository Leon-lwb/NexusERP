package com.nexus.erp.wm.service;

import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.erp.wm.domain.WmMaterialReceive;
import com.nexus.erp.wm.domain.bo.WmMaterialReceiveBo;
import com.nexus.erp.wm.domain.vo.WmMaterialReceiveVo;
import com.nexus.workflow.service.WfCallBackServiceI;

import java.util.List;

/**
 * 物料入库单Service接口
 * <p>
 * Q-003 重构后：本接口保留为兼容层，实际实现委托给 CrudService + ExecutorService。
 * writeBackPurchaseOrderReceivedQty 已移除（由 PuPurchaseReceiveWritebackService 承担）。
 *
 * @author nexus
 */
public interface IWmMaterialReceiveService extends WfCallBackServiceI {

    /**
     * 分页查询物料领用列表
     *
     * @param bo 物料领用业务对象（BO）
     * @param pageQuery 分页查询参数，包含页码、每页大小及排序条件
     * @return 分页查询结果
     */
    TableDataInfo<WmMaterialReceiveVo> selectPageList(WmMaterialReceiveBo bo, PageQuery pageQuery);

    /**
     * 查询物料领用列表
     *
     * @param bo 物料领用业务对象（BO）
     * @return 结果列表
     */
    List<WmMaterialReceiveVo> selectList(WmMaterialReceiveBo bo);

    /**
     * 查询物料领用
     *
     * @param id 主键 ID
     * @return 结果
     */
    WmMaterialReceiveVo selectById(Long id);

    /**
     * 新增物料领用
     *
     * @param bo 物料领用业务对象（BO）
     * @return 影响行数
     */
    int insertByBo(WmMaterialReceiveBo bo);

    /**
     * 修改物料领用
     *
     * @param bo 物料领用业务对象（BO）
     * @return 影响行数
     */
    int updateByBo(WmMaterialReceiveBo bo);

    /**
     * 删除物料领用
     *
     * @param ids 主键 ID 数组（支持批量操作）
     * @return 影响行数
     */
    int deleteByIds(Long[] ids);

    /**
     * 修改物料领用
     *
     * @param bo 物料领用业务对象（BO）
     * @return 操作是否成功
     */
    boolean updateWithValidation(WmMaterialReceiveBo bo);

    /**
     * 物料领用处理（submit）
     *
     * @param receiveIdStr 参数 receiveIdStr
     * @param selectFlowId selectFlow ID
     * @return 操作是否成功
     */
    boolean submit(String receiveIdStr, String selectFlowId);

    /**
     * 执行物料领用相关操作
     *
     * @param receiveId 入库单 ID
     * @return 操作是否成功
     */
    boolean executeReceive(Long receiveId);

    /**
     * 物料领用处理（confirmShelf）
     *
     * @param receiveId 入库单 ID
     * @return 操作是否成功
     */
    boolean confirmShelf(Long receiveId);

    /**
     * 获取物料领用
     *
     * @param receiveIdStr 参数 receiveIdStr
     * @return 结果字符串
     */
    String getProcInsId(String receiveIdStr);

    /**
     * 根据ID查询物料入库单实体
     *
     * @param id 主键ID
     * @return 物料入库单实体
     */
    WmMaterialReceive getById(Long id);

    /**
     * 更新物料入库单实体
     *
     * @param entity 物料入库单实体
     * @return 影响行数
     */
    int updateById(WmMaterialReceive entity);
}

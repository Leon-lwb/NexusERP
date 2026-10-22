package com.nexus.erp.wm.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.erp.wm.domain.WmMaterialReceive;
import com.nexus.erp.wm.domain.bo.WmMaterialReceiveBo;
import com.nexus.erp.wm.domain.vo.WmMaterialReceiveVo;
import com.nexus.erp.wm.mapper.WmMaterialReceiveMapper;
import com.nexus.erp.wm.service.IWmMaterialReceiveCrudService;
import com.nexus.erp.wm.service.IWmMaterialReceiveExecutorService;
import com.nexus.erp.wm.service.IWmMaterialReceiveService;
import com.nexus.erp.wm.service.WmCertifiedReceiptMutationGuard;
import com.nexus.erp.wm.service.WmCertifiedReceiptPostingService;
import com.nexus.workflow.domain.WfMyBusiness;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * 物料入库单Service实现类（兼容层）
 * <p>
 * Q-003 重构后：本类作为兼容层，将所有调用委托给 CrudService 和 ExecutorService。
 * pu Mapper 依赖已完全消除（PuPurchaseOrderMapper/PuPurchaseOrderLineMapper 不再注入）。
 * writeBackPurchaseOrderReceivedQty 已移除（由 PuPurchaseReceiveWritebackService 承担）。
 *
 * @author nexus
 */
@RequiredArgsConstructor
@DS("erp")
@Service
public class WmMaterialReceiveServiceImpl implements IWmMaterialReceiveService {

    private final WmCertifiedReceiptPostingService certifiedPosting;
    private final WmCertifiedReceiptMutationGuard certifiedReceiptGuard;

    /** 物料领用业务服务，提供物料领用相关能力 */
    private final IWmMaterialReceiveCrudService crudService;
    /** 执行器业务服务，提供执行器相关能力 */
    private final IWmMaterialReceiveExecutorService executorService;
    /** 物料入库单Mapper，用于轻量实体操作 */
    private final WmMaterialReceiveMapper materialReceiveMapper;

    /**
     * 分页查询物料领用列表
     *
     * @param bo 物料领用业务对象（BO）
     * @param pageQuery 分页查询参数，包含页码、每页大小及排序条件
     * @return 结果对象
     */
    @Override
    public TableDataInfo<WmMaterialReceiveVo> selectPageList(WmMaterialReceiveBo bo, PageQuery pageQuery) {
        return crudService.selectPageList(bo, pageQuery);
    }

    /**
     * 查询物料领用列表
     *
     * @param bo 物料领用业务对象（BO）
     * @return 结果对象
     */
    @Override
    public List<WmMaterialReceiveVo> selectList(WmMaterialReceiveBo bo) {
        return crudService.selectList(bo);
    }

    /**
     * 查询物料领用
     *
     * @param id 主键 ID
     * @return 结果
     */
    @Override
    public WmMaterialReceiveVo selectById(Long id) {
        return crudService.selectById(id);
    }

    /**
     * 新增物料领用
     *
     * @param bo 物料领用业务对象（BO）
     * @return 结果
     */
    @Override
    public int insertByBo(WmMaterialReceiveBo bo) {
        return crudService.insertByBo(bo);
    }

    /**
     * 修改物料领用
     *
     * @param bo 物料领用业务对象（BO）
     * @return 结果
     */
    @Override
    public int updateByBo(WmMaterialReceiveBo bo) {
        return crudService.updateByBo(bo);
    }

    /**
     * 删除物料领用
     *
     * @param ids 主键 ID 数组（支持批量操作）
     * @return 结果
     */
    @Override
    public int deleteByIds(Long[] ids) {
        return crudService.deleteByIds(ids);
    }

    /**
     * 修改物料领用
     *
     * @param bo 物料领用业务对象（BO）
     * @return 结果
     */
    @Override
    public boolean updateWithValidation(WmMaterialReceiveBo bo) {
        return crudService.updateWithValidation(bo);
    }

    /**
     * 物料领用处理（submit）
     *
     * @param receiveIdStr 参数 receiveIdStr
     * @param selectFlowId 所选流程 ID
     * @return 结果
     */
    @Override
    public boolean submit(String receiveIdStr, String selectFlowId) {
        return crudService.submit(receiveIdStr, selectFlowId);
    }

    /**
     * 执行物料领用相关操作
     *
     * @param receiveId 入库单 ID
     * @return 结果
     */
    @Override
    public boolean executeReceive(Long receiveId) {
        if (certifiedPosting.handles(receiveId)) {
            certifiedPosting.post(receiveId);
            return true;
        }
        return executorService.executeReceive(receiveId);
    }

    /**
     * 物料领用处理（confirmShelf）
     *
     * @param receiveId 入库单 ID
     * @return 结果
     */
    @Override
    public boolean confirmShelf(Long receiveId) {
        if(certifiedPosting.handlesShelf(receiveId)) return certifiedPosting.shelf(receiveId);
        return crudService.confirmShelf(receiveId);
    }

    /**
     * 获取物料领用
     *
     * @param receiveIdStr 参数 receiveIdStr
     * @return 结果
     */
    @Override
    public String getProcInsId(String receiveIdStr) {
        return crudService.getProcInsId(receiveIdStr);
    }

    /**
     * 物料领用处理（afterFlowHandle）
     *
     * @param business 参数 business
     * @return 结果
     */
    @Override
    public void afterFlowHandle(WfMyBusiness business) {
        crudService.afterFlowHandle(business);
    }

    /**
     * 获取物料领用详情
     *
     * @param id 主键 ID
     * @return 结果
     */
    @Override
    public Object getBusinessDataById(String id) {
        return crudService.getBusinessDataById(id);
    }

    /**
     * 物料领用处理（flowValuesOfTask）
     *
     * @param taskNameId 任务名称 ID
     * @param values 参数 values
     * @return 结果对象
     */
    @Override
    public Map<String, Object> flowValuesOfTask(String taskNameId, Map<String, Object> values) {
        return crudService.flowValuesOfTask(taskNameId, values);
    }

    /**
     * 物料领用处理（flowCandidateUsernamesOfTask）
     *
     * @param taskNameId 任务名称 ID
     * @param values 参数 values
     * @return 结果对象
     */
    @Override
    public List<String> flowCandidateUsernamesOfTask(String taskNameId, Map<String, Object> values) {
        return crudService.flowCandidateUsernamesOfTask(taskNameId, values);
    }

    /**
     * 根据ID查询物料入库单实体
     *
     * @param id 主键ID
     * @return 物料入库单实体
     */
    @Override
    public WmMaterialReceive getById(Long id) {
        return materialReceiveMapper.selectById(id);
    }

    /**
     * 更新物料入库单实体
     *
     * @param entity 物料入库单实体
     * @return 影响行数
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateById(WmMaterialReceive entity) {
        certifiedReceiptGuard.requireUnposted(entity.getReceiveId());
        return materialReceiveMapper.updateById(entity);
    }
}

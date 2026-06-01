package com.nexus.erp.wm.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.erp.wm.domain.bo.WmMaterialReceiveBo;
import com.nexus.erp.wm.domain.vo.WmMaterialReceiveVo;
import com.nexus.erp.wm.service.IWmMaterialReceiveCrudService;
import com.nexus.erp.wm.service.IWmMaterialReceiveExecutorService;
import com.nexus.erp.wm.service.IWmMaterialReceiveService;
import com.nexus.workflow.domain.WfMyBusiness;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

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

    private final IWmMaterialReceiveCrudService crudService;
    private final IWmMaterialReceiveExecutorService executorService;

    @Override
    public TableDataInfo<WmMaterialReceiveVo> selectPageList(WmMaterialReceiveBo bo, PageQuery pageQuery) {
        return crudService.selectPageList(bo, pageQuery);
    }

    @Override
    public List<WmMaterialReceiveVo> selectList(WmMaterialReceiveBo bo) {
        return crudService.selectList(bo);
    }

    @Override
    public WmMaterialReceiveVo selectById(Long id) {
        return crudService.selectById(id);
    }

    @Override
    public int insertByBo(WmMaterialReceiveBo bo) {
        return crudService.insertByBo(bo);
    }

    @Override
    public int updateByBo(WmMaterialReceiveBo bo) {
        return crudService.updateByBo(bo);
    }

    @Override
    public int deleteByIds(Long[] ids) {
        return crudService.deleteByIds(ids);
    }

    @Override
    public boolean updateWithValidation(WmMaterialReceiveBo bo) {
        return crudService.updateWithValidation(bo);
    }

    @Override
    public boolean submit(String receiveIdStr, String selectFlowId) {
        return crudService.submit(receiveIdStr, selectFlowId);
    }

    @Override
    public boolean executeReceive(Long receiveId) {
        return executorService.executeReceive(receiveId);
    }

    @Override
    public String getProcInsId(String receiveIdStr) {
        return crudService.getProcInsId(receiveIdStr);
    }

    @Override
    public void afterFlowHandle(WfMyBusiness business) {
        crudService.afterFlowHandle(business);
    }

    @Override
    public Object getBusinessDataById(String id) {
        return crudService.getBusinessDataById(id);
    }

    @Override
    public Map<String, Object> flowValuesOfTask(String taskNameId, Map<String, Object> values) {
        return crudService.flowValuesOfTask(taskNameId, values);
    }

    @Override
    public List<String> flowCandidateUsernamesOfTask(String taskNameId, Map<String, Object> values) {
        return crudService.flowCandidateUsernamesOfTask(taskNameId, values);
    }
}

package com.nexus.erp.wm.service;

import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.erp.wm.domain.WmProductionIssueLine;
import com.nexus.erp.wm.domain.bo.WmProductionIssueBo;
import com.nexus.erp.wm.domain.vo.WmMaterialStockVo;
import com.nexus.erp.wm.domain.vo.WmProductionIssueVo;

import java.util.List;

public interface IWmProductionIssueService {

    TableDataInfo<WmProductionIssueVo> selectPageList(WmProductionIssueBo bo, PageQuery pageQuery);

    List<WmProductionIssueVo> selectList(WmProductionIssueBo bo);

    WmProductionIssueVo selectById(Long id);

    int insertByBo(WmProductionIssueBo bo);

    int updateByBo(WmProductionIssueBo bo);

    int deleteByIds(Long[] ids);

    /**
     * 执行生产领料过账（扣减库存）— 支持多次部分领料
     */
    void execute(Long issueId);

    /**
     * 查询物料可用库存（ATP），按 FEFO 排序，供前端展示与执行前校验
     *
     * @param materialId  物料ID
     * @param warehouseId 仓库ID（可空）
     * @return 可用库存明细列表
     */
    List<WmMaterialStockVo> selectAvailableStock(Long materialId, Long warehouseId);

    /**
     * 根据工单 BOM 物料需求构建领料单行（排除倒冲物料），
     * 供前端"选择工单后自动带出应领明细"调用。
     *
     * @param woId 工单ID
     * @return 领料单行列表（未持久化，行ID为空）
     */
    List<WmProductionIssueLine> buildLinesFromWorkOrder(Long woId);
}

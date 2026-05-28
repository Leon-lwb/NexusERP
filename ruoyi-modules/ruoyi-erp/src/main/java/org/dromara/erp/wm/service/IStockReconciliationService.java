package org.dromara.erp.wm.service;

import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.erp.wm.domain.bo.WmStockReconciliationBo;
import org.dromara.erp.wm.domain.vo.WmStockReconciliationVo;

import java.util.List;

/**
 * 库存台账校验Service接口
 *
 * @author nexus
 */
public interface IStockReconciliationService {

    /**
     * 分页查询库存校验记录列表
     */
    TableDataInfo<WmStockReconciliationVo> selectPageList(WmStockReconciliationBo bo, PageQuery pageQuery);

    /**
     * 查询库存校验记录列表
     */
    List<WmStockReconciliationVo> selectList(WmStockReconciliationBo bo);

    /**
     * 查询库存校验记录详情
     */
    WmStockReconciliationVo selectById(Long id);

    /**
     * 删除库存校验记录
     */
    int deleteByIds(Long[] ids);

    /**
     * 执行全量库存台账校验
     * 比对 WmMaterialStock 库存数量�?WmMaterialStockTx 事务流水汇总的差异
     */
    void reconcile();

    /**
     * 按物料执行库存台账校�?     *
     * @param materialId 物料ID
     */
    void reconcileByMaterial(Long materialId);
}

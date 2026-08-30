package com.nexus.erp.wm.service;

import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.erp.wm.domain.WmProductionIssueLine;
import com.nexus.erp.wm.domain.bo.WmProductionIssueBo;
import com.nexus.erp.wm.domain.bo.pda.PdaIssueShortageBo;
import com.nexus.erp.wm.domain.vo.WmMaterialStockVo;
import com.nexus.erp.wm.domain.vo.WmProductionIssueVo;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
/**
 * 生产发料服务接口
 * <p>
 * 定义生产发料的业务契约，由对应的 ServiceImpl 实现。属于 ERP 仓储管理（wm）模块。
 * </p>
 */

public interface IWmProductionIssueService {

    /**
     * 分页查询生产发料列表
     *
     * @param bo 生产发料业务对象（BO）
     * @param pageQuery 分页查询参数，包含页码、每页大小及排序条件
     * @return 分页查询结果
     */
    TableDataInfo<WmProductionIssueVo> selectPageList(WmProductionIssueBo bo, PageQuery pageQuery);

    /**
     * 查询生产发料列表
     *
     * @param bo 生产发料业务对象（BO）
     * @return 结果列表
     */
    List<WmProductionIssueVo> selectList(WmProductionIssueBo bo);

    /**
     * 查询生产发料
     *
     * @param id 主键 ID
     * @return 结果
     */
    WmProductionIssueVo selectById(Long id);

    /**
     * 新增生产发料
     *
     * @param bo 生产发料业务对象（BO）
     * @return 影响行数
     */
    int insertByBo(WmProductionIssueBo bo);

    /**
     * 修改生产发料
     *
     * @param bo 生产发料业务对象（BO）
     * @return 影响行数
     */
    int updateByBo(WmProductionIssueBo bo);

    /**
     * 删除生产发料
     *
     * @param ids 主键 ID 数组（支持批量操作）
     * @return 影响行数
     */
    int deleteByIds(Long[] ids);

    /**
     * 执行生产领料过账（扣减库存）— 支持多次部分领料
     */
    void execute(Long issueId);

    /**
     * 缺货关闭完成（缺货逃生通道）：不再过账（实发量已在行级拣货时过账），
     * 未领满行按声明标记为缺货关闭（SHORT_CLOSED + 缺货原因），单据置为已完成。
     *
     * @param issueId   领料单ID
     * @param shortages 缺货声明行（所有未领满行必须被声明，不允许多余声明）
     */
    void completeWithShortage(Long issueId, List<PdaIssueShortageBo> shortages);

    /**
     * 查询物料可用库存（ATP），按 FEFO 排序，供前端展示与执行前校验
     *
     * @param materialId  物料ID
     * @param warehouseId 仓库ID（可空）
     * @return 可用库存明细列表
     */
    List<WmMaterialStockVo> selectAvailableStock(Long materialId, Long warehouseId);

    /**
     * 批量查询多个物料的总可用库存（汇总所有仓库/批次/库位），
     * 供领料明细列表展示每行物料的"可用库存"参考列。
     *
     * @param materialIds 物料ID列表
     * @return key=物料ID，value=该物料总可用量
     */
    Map<Long, BigDecimal> sumAvailableStockByMaterials(List<Long> materialIds);

    /**
     * 根据工单 BOM 物料需求构建领料单行（排除倒冲物料），
     * 供前端"选择工单后自动带出应领明细"调用。
     *
     * @param woId 工单ID
     * @return 领料单行列表（未持久化，行ID为空）
     */
    List<WmProductionIssueLine> buildLinesFromWorkOrder(Long woId);
}

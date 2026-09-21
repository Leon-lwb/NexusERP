package com.nexus.mes.eq.service;

import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.mes.eq.domain.EqSpareErpSync;
import com.nexus.mes.eq.domain.bo.EqSparePartBo;
import com.nexus.mes.eq.domain.bo.EqSpareStockBo;
import com.nexus.mes.eq.domain.vo.EqSparePartVo;
import com.nexus.mes.eq.domain.vo.EqSpareStockLogVo;

import java.math.BigDecimal;
import java.util.List;

/**
 * MES备件主数据 Service 业务层接口
 * <p>
 * <b>账本口径（MES-EQ-04）</b>：ERP 仓储 {@code wm_material_stock} 为唯一实物账本，MES 侧
 * {@code eq_spare_part.current_stock} 降级为派生冗余缓存；出入库统一经
 * {@code QueueConstants.MES_SPARE_ERP_QUEUE} 过账到 ERP，可用量一律按
 * 「在库 − 已预留，且 {@code frozen_flag = 0}」读取。
 *
 * @author Nexus
 */
public interface IEqSparePartService {

    /** 流水类型: 入库 */
    String TXN_IN = "IN";
    /** 流水类型: 出库 */
    String TXN_OUT = "OUT";
    /** 流水类型: 维修领用 */
    String TXN_REPAIR_USE = "REPAIR_USE";
    /** 流水类型: 退库(ERP 侧按入库过账) */
    String TXN_RETURN = "RETURN";

    /** 过账同步状态: 待投递 */
    String SYNC_PENDING = "PENDING";
    /** 过账同步状态: 已投递(未确认 ERP 落账) */
    String SYNC_SENT = "SENT";
    /** 过账同步状态: 已确认 ERP 落账 */
    String SYNC_SYNCED = "SYNCED";
    /** 过账同步状态: 投递失败(需重投) */
    String SYNC_FAILED = "FAILED";

    /**
     * 分页查询备件列表(填充 ERP 可用量与 lowStock 标记)
     *
     * @param bo 业务对象（含筛选条件/字段值）
     * @param pageQuery 分页参数（页码、每页大小、排序）
     * @return 分页结果
     */
    TableDataInfo<EqSparePartVo> selectPageList(EqSparePartBo bo, PageQuery pageQuery);

    /**
     * 根据ID获取备件详情
     *
     * @param spareId 备件ID
     * @return 返回结果
     */
    EqSparePartVo selectById(Long spareId);

    /**
     * 查询备件列表(不分页, 用于导出/下拉)
     *
     * @param bo 业务对象（含筛选条件/字段值）
     * @return 结果列表
     */
    List<EqSparePartVo> selectList(EqSparePartBo bo);

    /**
     * 新增备件主数据(校验 ERP 物料/仓库映射)
     *
     * @param bo 业务对象（含筛选条件/字段值）
     * @return 返回结果
     */
    EqSparePartVo insertByBo(EqSparePartBo bo);

    /**
     * 修改备件主数据(校验 ERP 物料/仓库映射)
     *
     * @param bo 业务对象（含筛选条件/字段值）
     * @return 是否成功
     */
    Boolean updateByBo(EqSparePartBo bo);

    /**
     * 批量删除备件(校验库存为0或null)
     *
     * @param ids ID数组
     * @return 是否成功
     */
    Boolean deleteByIds(Long[] ids);

    /**
     * 出入库操作(IN/RETURN 库存+, OUT/REPAIR_USE 库存-防超卖)
     * <p>
     * 同一事务内：① 校验 ERP 物料/仓库映射（严格模式缺失即拒绝）；② 原子更新 MES 派生缓存
     * {@code current_stock}；③ 写库存流水；④ 写 ERP 过账同步台账；⑤ 事务提交后投递 MQ 由 ERP 过账。
     * 任一步失败即回滚，不留半成品；投递失败写 {@code FAILED} 并可重投（不静默）。
     *
     * @param bo 出入库参数(spareId/transactionType/quantity/refType/refId)
     * @return 变动后 MES 派生缓存库存
     */
    BigDecimal stockTransaction(EqSpareStockBo bo);

    /**
     * 分页查询库存流水
     *
     * @param bo 业务对象（含筛选条件/字段值）
     * @param pageQuery 分页参数（页码、每页大小、排序）
     * @return 分页结果
     */
    TableDataInfo<EqSpareStockLogVo> selectStockLogPage(EqSpareStockBo bo, PageQuery pageQuery);

    /**
     * 查询库存流水列表(不分页, 用于导出)
     *
     * @param bo 业务对象（含筛选条件/字段值）
     * @return 结果列表
     */
    List<EqSpareStockLogVo> selectStockLogList(EqSpareStockBo bo);

    /**
     * 查询低于安全库存的备件
     * <p>口径：ERP 可用量（在库 − 已预留，且 {@code frozen_flag = 0}）&lt; 安全库存；
     * 未映射 ERP 物料的备件以 {@code lowStockBasis = NOT_MAPPED} 一并返回（需先完成映射）。</p>
     *
     * @return 结果列表
     */
    List<EqSparePartVo> selectLowStock();

    /**
     * 低库存一键生成 ERP 采购申请（经 MQ 交由 ERP 采购申请服务落单）
     *
     * @param spareIds 备件ID集合；为空时取当前全部低库存备件
     * @param remark   备注
     * @return 提交的请购行数
     */
    int generatePurchaseRequest(List<Long> spareIds, String remark);

    /**
     * 查询备件 → ERP 过账同步台账
     *
     * @param syncStatus 同步状态过滤(可空)
     * @return 台账列表
     */
    List<EqSpareErpSync> selectErpSyncList(String syncStatus);

    /**
     * 重投/对账单笔过账
     * <p>先按业务幂等键查 ERP 流水：已落账则标记 {@code SYNCED} 并回写 MES 派生缓存；
     * 未落账则重新投递（重投次数 +1），投递失败写 {@code FAILED} 与失败原因。</p>
     *
     * @param syncId 同步台账ID
     * @return 是否已确认 ERP 落账
     */
    boolean retryErpSync(Long syncId);

    /**
     * 存量备件默认仓库回填（MES-EQ-04 收口）
     * <p>把 {@code warehouse_id IS NULL AND del_flag = '0'} 的备件统一回填为服务端配置的
     * {@code nexus.mes.eq.spare.default-warehouse-id}（幂等：已映射的备件不动）。</p>
     * <p><b>默认仓库不接受客户端传参</b>（避免任意仓库覆盖）；配置为空、配置非法或该仓库在 ERP
     * {@code wm_warehouse} 中不存在/未启用时抛 {@link com.nexus.common.core.exception.ServiceException}。</p>
     *
     * @return 影响行数
     */
    int backfillWarehouse();
}

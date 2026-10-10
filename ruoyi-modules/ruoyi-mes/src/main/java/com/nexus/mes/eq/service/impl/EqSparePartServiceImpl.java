package com.nexus.mes.eq.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.dynamic.datasource.toolkit.DynamicDataSourceContextHolder;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.common.satoken.utils.LoginHelper;
import com.nexus.common.tenant.helper.TenantHelper;
import com.nexus.mes.eq.domain.EqSpareErpSync;
import com.nexus.mes.eq.domain.EqSparePart;
import com.nexus.mes.eq.domain.EqSpareStockLog;
import com.nexus.mes.eq.domain.bo.EqSparePartBo;
import com.nexus.mes.eq.domain.bo.EqSpareStockBo;
import com.nexus.mes.eq.domain.vo.EqSparePartVo;
import com.nexus.mes.eq.domain.vo.EqSpareStockLogVo;
import com.nexus.mes.eq.mapper.EqSpareErpSyncMapper;
import com.nexus.mes.eq.mapper.EqSparePartMapper;
import com.nexus.mes.eq.mapper.EqSpareStockLogMapper;
import com.nexus.mes.eq.mapper.ErpSpareStockQueryMapper;
import com.nexus.mes.eq.mq.EqSpareErpMqPublisher;
import com.nexus.mes.eq.service.IEqSparePartService;
import com.nexus.mes.eq.service.support.EqSpareValueValidator;
import com.nexus.system.strategy.AutoCodeUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * MES 备件主数据业务层服务实现
 * <p>
 * 数据源: ry-mes 库；ERP 只读查询经 {@code DynamicDataSourceContextHolder.push("erp")} 切换。
 * <p>
 * <b>账本口径（MES-EQ-04 MVP）</b>：
 * <ul>
 *   <li><b>权威账本</b>：ERP {@code wm_material_stock}（在库/预留/冻结），所有出入库经 MQ
 *       {@code MES_SPARE_ERP_QUEUE} 过账，ERP 侧 {@code WmSpareStockPostingServiceImpl} 用
 *       {@code deductStockAvailable}（可用量守卫）/ {@code addStock} 原子增减；</li>
 *   <li><b>MES {@code current_stock}</b>：ERP 库存的派生冗余缓存。同步方向 ERP → MES；时机 =
 *       ① 出入库时按同一增量本地维护（保证 MES 页面连续可用）② ERP 落账确认后（{@link #retryErpSync}）
 *       以 ERP 可用量整体覆盖回写；</li>
 *   <li><b>可用量口径</b>：{@code availableOf = SUM(stock_quantity − reserved_quantity)}（{@code frozen_flag = 0}），
 *       低库存与采购建议全部按此口径；</li>
 *   <li><b>幂等</b>：业务幂等键 {@code EQSP:<tenantId>:<logId>}，MES 侧唯一约束
 *       {@code uk_tenant_biz_key}，ERP 侧 Redis 原子占位 + 流水查重；</li>
 *   <li><b>失败可见</b>：投递失败写 {@code eq_spare_erp_sync.sync_status = FAILED} 与失败原因，
 *       提供 {@code /erp-sync/failed} 列表与 {@code /erp-sync/{id}/retry} 重投，绝不静默。</li>
 * </ul>
 * <p>
 * <b>⚠ 跨库只读与事务互斥（重要，勿回退）</b>：本类标 {@code @DS("mes")}，跨库 ERP 只读经
 * {@code DynamicDataSourceContextHolder.push("erp")} 编程式切换。<b>因此调用
 * {@link #queryErpAvailable} / {@link #queryErpMaterial} / {@link #validateErpMapping} 的公开方法
 * 不得开启事务</b>：Spring 事务开启时已按类级 {@code @DS("mes")} 把 mes 连接绑定到线程，
 * 之后再 {@code push("erp")} 不会重新路由（{@code DataSourceUtils.getConnection} 复用已绑定连接），
 * 查询会打到 {@code ry-mes} 并报“表 {@code ry-mes.bd_material} 不存在”。
 * 故 {@link #insertByBo} / {@link #updateByBo} / {@link #backfillWarehouse} <b>刻意不加</b>
 * {@code @Transactional}（各自只有一条写语句，单语句本身原子；备件编码唯一性由 数据库唯一键
 * {@code uk_tenant_code(tenant_id, spare_code)} 兜底）。
 * 防回归断言见 {@code EqSparePartServiceImplTest#erpCrossDbReadMethodsMustNotBeTransactional}。
 *
 * @author Nexus
 */
@Slf4j
@RequiredArgsConstructor
@DS("mes")
@Service("eqSparePartService")
public class EqSparePartServiceImpl implements IEqSparePartService {

    /** 低库存判定口径: 读 ERP 可用量 */
    private static final String BASIS_ERP_AVAILABLE = "ERP_AVAILABLE";
    /** 低库存判定口径: 未映射 ERP 物料, 无法判定 */
    private static final String BASIS_NOT_MAPPED = "NOT_MAPPED";
    /** 低库存判定口径: ERP 不可达, 降级用 MES 派生缓存 */
    private static final String BASIS_ERP_UNAVAILABLE = "ERP_UNAVAILABLE";
    /** 过账幂等键前缀 */
    private static final String BIZ_KEY_PREFIX = "EQSP:";

    private final EqSparePartMapper baseMapper;
    private final EqSpareStockLogMapper stockLogMapper;
    private final EqSpareErpSyncMapper erpSyncMapper;
    private final ErpSpareStockQueryMapper erpSpareStockQueryMapper;
    private final EqSpareErpMqPublisher erpMqPublisher;
    private final AutoCodeUtil autoCodeUtil;

    /**
     * ERP 严格模式: 未映射 {@code material_id}/{@code warehouse_id} 的备件拒绝出入库（默认开启，
     * 避免"只改 MES 台账、ERP 不动账"的两套账复发）。可用配置 {@code nexus.mes.eq.spare.erp-strict=false} 放宽。
     */
    @Value("${nexus.mes.eq.spare.erp-strict:true}")
    private boolean erpStrict = true;

    /**
     * 备件默认 ERP 仓库ID（存量 {@code warehouse_id} 回填入口专用，MES-EQ-04 收口）。
     * <p>默认空值：未配置时回填入口抛业务异常并提示运维配置；<b>不作为客户端可传参数</b>，
     * 避免任意仓库覆盖。配置形如 {@code nexus.mes.eq.spare.default-warehouse-id: 1}。</p>
     */
    @Value("${nexus.mes.eq.spare.default-warehouse-id:}")
    private String defaultWarehouseId;

    /**
     * 分页查询备件零件列表
     *
     * @param bo 业务对象（含筛选条件/字段值）
     * @param pageQuery 分页参数（页码、每页大小、排序）
     * @return 分页结果
     */
    @Override
    public TableDataInfo<EqSparePartVo> selectPageList(EqSparePartBo bo, PageQuery pageQuery) {
        Page<EqSparePartVo> page = baseMapper.selectVoPage(pageQuery.build(), buildQueryWrapper(bo));
        fillErpStock(page.getRecords());
        return TableDataInfo.build(page);
    }

    /**
     * 获取备件零件详情
     *
     * @param spareId 备件ID
     * @return 返回结果
     */
    @Override
    public EqSparePartVo selectById(Long spareId) {
        EqSparePartVo vo = baseMapper.selectVoById(spareId);
        if (vo != null) {
            fillErpStock(List.of(vo));
        }
        return vo;
    }

    /**
     * 查询备件零件列表
     *
     * @param bo 业务对象（含筛选条件/字段值）
     * @return 结果列表
     */
    @Override
    public List<EqSparePartVo> selectList(EqSparePartBo bo) {
        List<EqSparePartVo> list = baseMapper.selectVoList(buildQueryWrapper(bo));
        fillErpStock(list);
        return list;
    }

    /**
     * 新增备件零件
     * <p><b>刻意不加 {@code @Transactional}</b>：本方法会经 {@link #validateErpMapping} 做跨库 ERP 只读
     * （{@code push("erp")}），事务开启后连接已绑定 {@code mes} 数据源，跨库读会失效；且本方法只有
     * 一条写语句（{@code insert}），单语句自身原子。详见类文档「跨库只读与事务互斥」。</p>
     *
     * @param bo 业务对象（含筛选条件/字段值）
     * @return 返回结果
     */
    @Override
    public EqSparePartVo insertByBo(EqSparePartBo bo) {
        validateMasterValues(bo);
        if (StringUtils.isBlank(bo.getSpareCode())) {
            bo.setSpareCode(autoCodeUtil.genSerialCode("MES_SPARE_CODE", null));
        }
        /** 校验编码唯一 */
        Long cnt = baseMapper.selectCount(Wrappers.<EqSparePart>lambdaQuery()
            .eq(EqSparePart::getSpareCode, bo.getSpareCode()));
        if (cnt != null && cnt > 0) {
            throw new ServiceException("备件编码已存在: " + bo.getSpareCode());
        }
        /** MES-EQ-04: 映射维护 —— 校验 ERP 物料存在且启用、仓库存在 */
        validateErpMapping(bo.getMaterialId(), bo.getWarehouseId());
        EqSparePart spare = new EqSparePart();
        spare.setSpareCode(bo.getSpareCode());
        spare.setSpareName(bo.getSpareName());
        spare.setSpec(bo.getSpec());
        spare.setModel(bo.getModel());
        spare.setUnit(bo.getUnit());
        spare.setCategory(bo.getCategory());
        spare.setSafetyStock(bo.getSafetyStock() != null ? bo.getSafetyStock() : BigDecimal.ZERO);
        spare.setCurrentStock(BigDecimal.ZERO);
        spare.setMaterialId(bo.getMaterialId());
        spare.setWarehouseId(bo.getWarehouseId());
        /** 备件参考单价(成本口径): 主数据人工维护, 缺省 0; 出入库过账单价一律服务端读库填充 */
        spare.setUnitCost(bo.getUnitCost() != null ? bo.getUnitCost() : BigDecimal.ZERO);
        spare.setRemark(bo.getRemark());
        baseMapper.insert(spare);
        log.info("新增备件: spareId={}, code={}, materialId={}, warehouseId={}",
            spare.getSpareId(), spare.getSpareCode(), spare.getMaterialId(), spare.getWarehouseId());
        return baseMapper.selectVoById(spare.getSpareId());
    }

    /**
     * 更新备件零件
     * <p><b>刻意不加 {@code @Transactional}</b>：同 {@link #insertByBo}，需先做跨库 ERP 只读校验，
     * 且只有一条写语句（{@code updateById}）。</p>
     *
     * @param bo 业务对象（含筛选条件/字段值）
     * @return 是否成功
     */
    @Override
    public Boolean updateByBo(EqSparePartBo bo) {
        validateMasterValues(bo);
        if (bo.getSpareId() == null) {
            throw new ServiceException("备件ID不能为空");
        }
        if (StringUtils.isBlank(bo.getSpareCode())) {
            throw new ServiceException("备件编码不能为空");
        }
        if (baseMapper.selectById(bo.getSpareId()) == null) {
            throw new ServiceException("备件不存在: " + bo.getSpareId());
        }
        Long duplicates = baseMapper.selectCount(Wrappers.<EqSparePart>lambdaQuery()
            .eq(EqSparePart::getSpareCode, bo.getSpareCode())
            .ne(EqSparePart::getSpareId, bo.getSpareId()));
        if (duplicates != null && duplicates > 0) {
            throw new ServiceException("备件编码已存在: " + bo.getSpareCode());
        }
        validateErpMapping(bo.getMaterialId(), bo.getWarehouseId());
        EqSparePart spare = new EqSparePart();
        spare.setSpareId(bo.getSpareId());
        spare.setSpareCode(bo.getSpareCode());
        spare.setSpareName(bo.getSpareName());
        spare.setSpec(bo.getSpec());
        spare.setModel(bo.getModel());
        spare.setUnit(bo.getUnit());
        spare.setCategory(bo.getCategory());
        spare.setSafetyStock(bo.getSafetyStock());
        spare.setMaterialId(bo.getMaterialId());
        spare.setWarehouseId(bo.getWarehouseId());
        spare.setUnitCost(bo.getUnitCost());
        spare.setRemark(bo.getRemark());
        return baseMapper.updateById(spare) > 0;
    }

    /**
     * 获取备件零件详情
     *
     * @param ids ID数组
     * @return 是否成功
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean deleteByIds(Long[] ids) {
        if (ids == null || ids.length == 0) {
            return false;
        }
        /** 校验库存为0才允许删除 */
        List<EqSparePartVo> list = baseMapper.selectVoList(Wrappers.<EqSparePart>lambdaQuery()
            .in(EqSparePart::getSpareId, Arrays.asList(ids)));
        for (EqSparePartVo v : list) {
            if (v.getCurrentStock() != null && v.getCurrentStock().compareTo(BigDecimal.ZERO) != 0) {
                throw new ServiceException("备件[" + v.getSpareCode() + "]库存不为0, 无法删除");
            }
        }
        return baseMapper.deleteByIds(Arrays.asList(ids)) > 0;
    }

    /**
     * 出入库: MES 派生缓存原子更新 + 流水 + ERP 过账同步台账 + 事务提交后投递 MQ。
     * <p>IN/RETURN: current_stock = current_stock + qty；
     * OUT/REPAIR_USE: current_stock = current_stock - qty（WHERE current_stock &gt;= qty，不足即拒绝且不留半成品）。</p>
     *
     * @param bo 业务对象（含筛选条件/字段值）
     * @return 变动后库存
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public BigDecimal stockTransaction(EqSpareStockBo bo) {
        String type = bo.getTransactionType();
        if (!TXN_IN.equals(type) && !TXN_OUT.equals(type)
            && !TXN_REPAIR_USE.equals(type) && !TXN_RETURN.equals(type)) {
            throw new ServiceException("非法流水类型: " + type);
        }
        EqSparePartVo spare = baseMapper.selectVoById(bo.getSpareId());
        if (spare == null) {
            throw new ServiceException("备件不存在: " + bo.getSpareId());
        }
        BigDecimal qty = bo.getQuantity();
        /** 数量校验：必须非空且大于 0，防止 null 触发 NPE、负值绕过防超卖/反向加库存 */
        EqSpareValueValidator.requirePositiveQuantity(qty, "备件出入库数量");
        /** MES-EQ-04: 映射缺失时行为明确 —— 拒绝并提示, 不静默只改 MES 台账 */
        assertErpMapping(spare);

        boolean inbound = TXN_IN.equals(type) || TXN_RETURN.equals(type);
        BigDecimal before = spare.getCurrentStock() == null ? BigDecimal.ZERO : spare.getCurrentStock();
        BigDecimal computedAfter;
        int rows;
        if (inbound) {
            computedAfter = before.add(qty);
            rows = baseMapper.update(null, Wrappers.<EqSparePart>lambdaUpdate()
                .setSql("current_stock = current_stock + {0}", qty.toPlainString())
                .eq(EqSparePart::getSpareId, bo.getSpareId())
                .le(EqSparePart::getCurrentStock, EqSpareValueValidator.MAX_QUANTITY.subtract(qty)));
        } else {
            /** OUT / REPAIR_USE: 防超卖 */
            computedAfter = before.subtract(qty);
            rows = baseMapper.update(null, Wrappers.<EqSparePart>lambdaUpdate()
                .setSql("current_stock = current_stock - {0}", qty.toPlainString())
                .eq(EqSparePart::getSpareId, bo.getSpareId())
                .ge(EqSparePart::getCurrentStock, qty));
        }
        if (rows == 0) {
            if (inbound) {
                throw new ServiceException("备件库存超出允许范围或记录已变更，请刷新后重试");
            }
            throw new ServiceException("库存不足, 当前库存: " + before + ", 需扣减: " + qty);
        }
        /**
         * 回读落库后的实际库存作为流水基准(MES-EQ-14): 原子 SQL 只保证"增量"正确,
         * 而 before/after 由 Java 先算, 在并发出入库下会与实际 current_stock 漂移。
         */
        BigDecimal actualAfter = readCurrentStock(bo.getSpareId());
        BigDecimal after = actualAfter == null ? computedAfter : actualAfter;
        BigDecimal beforeQty = inbound ? after.subtract(qty) : after.add(qty);
        /** 写流水 */
        EqSpareStockLog logEntry = new EqSpareStockLog();
        logEntry.setSpareId(bo.getSpareId());
        logEntry.setSpareCode(spare.getSpareCode());
        logEntry.setSpareName(spare.getSpareName());
        logEntry.setTransactionType(type);
        logEntry.setQuantity(qty);
        logEntry.setBeforeQty(beforeQty);
        logEntry.setAfterQty(after);
        logEntry.setRefType(StringUtils.isNotBlank(bo.getRefType()) ? bo.getRefType() : "MANUAL");
        logEntry.setRefId(bo.getRefId());
        logEntry.setOperatorId(LoginHelper.getUserId());
        logEntry.setOperatorName(LoginHelper.getUsername());
        logEntry.setOperateTime(new Date());
        logEntry.setRemark(bo.getRemark());
        stockLogMapper.insert(logEntry);
        /** MES-EQ-04: 同事务写 ERP 过账同步台账(业务幂等键 = {@code EQSP:<tenantId>:<logId>}) */
        EqSpareErpSync sync = buildSync(spare, logEntry, type, qty, bo);
        erpSyncMapper.insert(sync);
        /** 事务提交后投递: 避免回滚产生"幻影过账", 投递失败落 FAILED 并保留失败原因 */
        publishAfterCommit(sync);
        log.info("备件出入库: spareId={}, type={}, qty={}, before={}, after={}, bizKey={}",
            bo.getSpareId(), type, qty, beforeQty, after, sync.getBizKey());
        return after;
    }

    /**
     * 回读备件落库后的实际库存(MES-EQ-14)。
     *
     * @param spareId 备件ID
     * @return 实际 current_stock; 读不到(记录被删/异常)时返回 null, 由调用方回退到 Java 计算值
     */
    private BigDecimal readCurrentStock(Long spareId) {
        EqSparePart current = baseMapper.selectById(spareId);
        if (current == null) {
            return null;
        }
        return current.getCurrentStock() == null ? BigDecimal.ZERO : current.getCurrentStock();
    }

    /**
     * 分页查询流水列表
     *
     * @param bo 业务对象（含筛选条件/字段值）
     * @param pageQuery 分页参数（页码、每页大小、排序）
     * @return 分页结果
     */
    @Override
    public TableDataInfo<EqSpareStockLogVo> selectStockLogPage(EqSpareStockBo bo, PageQuery pageQuery) {
        Page<EqSpareStockLogVo> page = stockLogMapper.selectVoPage(pageQuery.build(), buildStockLogQueryWrapper(bo));
        return TableDataInfo.build(page);
    }

    /**
     * 查询流水列表
     *
     * @param bo 业务对象（含筛选条件/字段值）
     * @return 结果列表
     */
    @Override
    public List<EqSpareStockLogVo> selectStockLogList(EqSpareStockBo bo) {
        return stockLogMapper.selectVoList(buildStockLogQueryWrapper(bo));
    }

    /**
     * 查询低库存（口径：ERP 可用量 = 在库 − 已预留，且 frozen_flag = 0）
     *
     * @return 结果列表
     */
    @Override
    public List<EqSparePartVo> selectLowStock() {
        List<EqSparePartVo> list = baseMapper.selectVoList(Wrappers.<EqSparePart>lambdaQuery()
            .isNotNull(EqSparePart::getSafetyStock));
        fillErpStock(list);
        return list.stream().filter(v -> Boolean.TRUE.equals(v.getLowStock())).collect(Collectors.toList());
    }

    /**
     * 低库存自动申请尚未接入 ERP 逐来源占用账，服务端暂停消息投递
     *
     * @param spareIds 备件ID集合；为空时取当前全部低库存备件
     * @param remark   备注
     * @throws ServiceException 来源占用尚不可用时拒绝请购
     */
    @Override
    public int generatePurchaseRequest(List<Long> spareIds, String remark) {
        /**
         * 与 ERP 申请行共用物料护栏/逐需求占用账之前，不得异步返回“已提交”
         * 却在消费者里无来源创建草稿；无需读取库存或向 MQ 发布采购消息。
         */
        throw new ServiceException("备件低库存自动请购已暂停：采购需求来源占用尚未核验，请在 ERP 人工关联需求后办理");
    }

    /**
     * 查询备件 → ERP 过账同步台账
     *
     * @param syncStatus 同步状态过滤(可空)
     * @return 台账列表
     */
    @Override
    public List<EqSpareErpSync> selectErpSyncList(String syncStatus) {
        return erpSyncMapper.selectList(Wrappers.<EqSpareErpSync>lambdaQuery()
            .eq(StringUtils.isNotBlank(syncStatus), EqSpareErpSync::getSyncStatus, syncStatus)
            .orderByDesc(EqSpareErpSync::getCreateTime));
    }

    /**
     * 重投/对账单笔过账
     *
     * @param syncId 同步台账ID
     * @return 是否已确认 ERP 落账
     */
    @Override
    public boolean retryErpSync(Long syncId) {
        EqSpareErpSync sync = erpSyncMapper.selectById(syncId);
        if (sync == null) {
            throw new ServiceException("过账同步记录不存在: " + syncId);
        }
        if (SYNC_SYNCED.equals(sync.getSyncStatus())) {
            return true;
        }
        /** 先对账: ERP 已落账则直接确认, 避免重复过账 */
        if (countErpPosting(sync.getBizKey()) > 0) {
            markSync(sync, SYNC_SYNCED, null);
            refreshDerivedStock(sync);
            return true;
        }
        EqSparePartVo spare = baseMapper.selectVoById(sync.getSpareId());
        if (spare == null) {
            throw new ServiceException("备件不存在, 无法重投: " + sync.getSpareId());
        }
        assertSyncMapping(sync);
        Map<String, Object> payload = buildStockPayload(spare, sync);
        try {
            erpMqPublisher.publish(payload);
            sync.setRetryCount(sync.getRetryCount() == null ? 1 : sync.getRetryCount() + 1);
            markSync(sync, SYNC_SENT, null);
        } catch (Exception e) {
            log.error("备件过账重投失败: bizKey={}", sync.getBizKey(), e);
            markSync(sync, SYNC_FAILED, e.getMessage());
            throw new ServiceException("备件 ERP 过账重投失败: " + e.getMessage());
        }
        return false;
    }

    /**
     * 存量备件默认仓库回填（MES-EQ-04 收口）
     * <p>仅回填 {@code warehouse_id IS NULL AND del_flag = '0'} 的备件（幂等，已映射的不动）；
     * 回填前跨库只读校验目标仓库在 ERP {@code wm_warehouse} 中<b>存在且启用</b>
     * （{@code status = 1 AND del_flag = '0'} 且同租户）。</p>
     * <p><b>刻意不加 {@code @Transactional}</b>：方法内含跨库 ERP 只读（{@code push("erp")}），
     * 事务开启后连接已绑定 {@code mes}，push 不生效会打到 {@code ry-mes} 报
     * “表 {@code ry-mes.wm_warehouse} 不存在”；且只有一条 UPDATE，单语句自身原子。</p>
     *
     * @return 影响行数
     */
    @Override
    public int backfillWarehouse() {
        if (StringUtils.isBlank(defaultWarehouseId)) {
            throw new ServiceException("未配置 nexus.mes.eq.spare.default-warehouse-id，请先配置备件默认仓库");
        }
        Long warehouseId;
        try {
            warehouseId = Long.valueOf(defaultWarehouseId.trim());
        } catch (NumberFormatException e) {
            throw new ServiceException("nexus.mes.eq.spare.default-warehouse-id 配置非法(需为仓库ID数字): "
                + defaultWarehouseId);
        }
        /** 跨库只读校验: 仓库存在且启用(否则回填后过账会打到已停用仓库) */
        long enabled;
        try {
            DynamicDataSourceContextHolder.push("erp");
            enabled = erpSpareStockQueryMapper.countEnabledWarehouse(currentTenantId(), warehouseId);
        } finally {
            DynamicDataSourceContextHolder.poll();
        }
        if (enabled <= 0) {
            throw new ServiceException("ERP 仓库不存在或已停用, 无法作为备件默认仓库: warehouseId=" + warehouseId);
        }
        int rows = baseMapper.update(null, Wrappers.<EqSparePart>lambdaUpdate()
            .set(EqSparePart::getWarehouseId, warehouseId)
            .isNull(EqSparePart::getWarehouseId)
            .eq(EqSparePart::getDelFlag, "0"));
        log.info("备件默认仓库回填完成: warehouseId={}, rows={}", warehouseId, rows);
        return rows;
    }

    private LambdaQueryWrapper<EqSparePart> buildQueryWrapper(EqSparePartBo bo) {
        return Wrappers.<EqSparePart>lambdaQuery()
            .like(StringUtils.isNotBlank(bo.getSpareCode()), EqSparePart::getSpareCode, bo.getSpareCode())
            .like(StringUtils.isNotBlank(bo.getSpareName()), EqSparePart::getSpareName, bo.getSpareName())
            .eq(StringUtils.isNotBlank(bo.getCategory()), EqSparePart::getCategory, bo.getCategory())
            .eq(bo.getMaterialId() != null, EqSparePart::getMaterialId, bo.getMaterialId())
            .orderByDesc(EqSparePart::getCreateTime);
    }

    private LambdaQueryWrapper<EqSpareStockLog> buildStockLogQueryWrapper(EqSpareStockBo bo) {
        return Wrappers.<EqSpareStockLog>lambdaQuery()
            .eq(bo.getSpareId() != null, EqSpareStockLog::getSpareId, bo.getSpareId())
            .eq(StringUtils.isNotBlank(bo.getSpareCode()), EqSpareStockLog::getSpareCode, bo.getSpareCode())
            .eq(StringUtils.isNotBlank(bo.getTransactionType()), EqSpareStockLog::getTransactionType, bo.getTransactionType())
            .eq(StringUtils.isNotBlank(bo.getRefType()), EqSpareStockLog::getRefType, bo.getRefType())
            .orderByDesc(EqSpareStockLog::getOperateTime);
    }

    /**
     * 填充 ERP 可用量与低库存标记（口径：在库 − 已预留，且 frozen_flag = 0）
     *
     * @param list 备件视图列表
     */
    private void fillErpStock(List<EqSparePartVo> list) {
        if (list == null || list.isEmpty()) {
            return;
        }
        List<Long> materialIds = list.stream()
            .map(EqSparePartVo::getMaterialId)
            .filter(Objects::nonNull)
            .distinct()
            .collect(Collectors.toList());
        Map<Long, BigDecimal> available = queryErpAvailable(materialIds);
        for (EqSparePartVo v : list) {
            BigDecimal safe = v.getSafetyStock() == null ? BigDecimal.ZERO : v.getSafetyStock();
            if (v.getMaterialId() == null) {
                /** 未映射: 无法判定 ERP 可用量, 显式提示而非静默用 MES 旧账 */
                v.setErpMapped(false);
                v.setErpAvailableStock(null);
                v.setLowStockBasis(BASIS_NOT_MAPPED);
                v.setLowStock(true);
            } else if (available == null) {
                /** ERP 不可达: 降级用 MES 派生缓存, 并显式标注口径 */
                BigDecimal cur = v.getCurrentStock() == null ? BigDecimal.ZERO : v.getCurrentStock();
                v.setErpMapped(true);
                v.setErpAvailableStock(null);
                v.setLowStockBasis(BASIS_ERP_UNAVAILABLE);
                v.setLowStock(cur.compareTo(safe) < 0);
            } else {
                BigDecimal a = available.getOrDefault(v.getMaterialId(), BigDecimal.ZERO);
                v.setErpMapped(true);
                v.setErpAvailableStock(a);
                v.setLowStockBasis(BASIS_ERP_AVAILABLE);
                v.setLowStock(a.compareTo(safe) < 0);
            }
        }
    }

    /**
     * 查询 ERP 可用量（按物料汇总，在库 − 已预留，且 frozen_flag = 0）
     * <p><b>⚠ 调用方不得处于事务中</b>：本方法用 {@code DynamicDataSourceContextHolder.push("erp")}
     * 编程式切库，事务开启时连接已按类级 {@code @DS("mes")} 绑定到线程，push 不会重新路由，
     * 查询会打到 {@code ry-mes} 报“表 {@code ry-mes.wm_material_stock} 不存在”。</p>
     *
     * @param materialIds 物料ID集合
     * @return 物料 → 可用量；ERP 不可达时返回 {@code null}（调用方降级并显式标注口径）
     */
    private Map<Long, BigDecimal> queryErpAvailable(List<Long> materialIds) {
        if (materialIds == null || materialIds.isEmpty()) {
            return Collections.emptyMap();
        }
        try {
            DynamicDataSourceContextHolder.push("erp");
            List<Map<String, Object>> rows = erpSpareStockQueryMapper
                .selectAvailableByMaterialIds(currentTenantId(), materialIds);
            Map<Long, BigDecimal> result = new HashMap<>(materialIds.size());
            for (Map<String, Object> row : rows) {
                Object mid = row.get("materialId");
                if (mid == null) {
                    continue;
                }
                result.put(Long.valueOf(String.valueOf(mid)), toDecimal(row.get("availableQty")));
            }
            return result;
        } catch (Exception e) {
            log.error("查询 ERP 库存可用量失败, 低库存判定降级为 MES 派生缓存: materialIds={}", materialIds, e);
            return null;
        } finally {
            DynamicDataSourceContextHolder.poll();
        }
    }

    /**
     * 查询 ERP 物料主数据（用于映射校验与请购行冗余字段）
     * <p><b>⚠ 调用方不得处于事务中</b>（同 {@link #queryErpAvailable}）。</p>
     *
     * @param materialId 物料ID
     * @return 物料 Map；不存在返回 null
     */
    private Map<String, Object> queryErpMaterial(Long materialId) {
        if (materialId == null) {
            return null;
        }
        try {
            DynamicDataSourceContextHolder.push("erp");
            return erpSpareStockQueryMapper.selectMaterial(currentTenantId(), materialId);
        } catch (Exception e) {
            log.error("查询 ERP 物料失败: materialId={}", materialId, e);
            return null;
        } finally {
            DynamicDataSourceContextHolder.poll();
        }
    }

    /**
     * 主数据数值的服务层防御校验，覆盖不经过 Controller 的内部调用。
     *
     * @param bo 备件主数据
     */
    private void validateMasterValues(EqSparePartBo bo) {
        if (bo == null) {
            throw new ServiceException("备件主数据不能为空");
        }
        EqSpareValueValidator.validateOptionalQuantity(bo.getSafetyStock(), "安全库存");
        EqSpareValueValidator.validateOptionalCost(bo.getUnitCost());
    }

    /**
     * 校验 ERP 物料/仓库映射（存在 + 物料状态启用）
     * <p><b>⚠ 调用方不得处于事务中</b>（同 {@link #queryErpAvailable}）：
     * 事务内 {@code push("erp")} 失效，会报“表 {@code ry-mes.bd_material} 不存在”。
     * 防回归断言见 {@code EqSparePartServiceImplTest#erpCrossDbReadMethodsMustNotBeTransactional}。</p>
     *
     * @param materialId  ERP 物料ID(可空)
     * @param warehouseId ERP 仓库ID(可空)
     */
    private void validateErpMapping(Long materialId, Long warehouseId) {
        if (materialId == null && warehouseId == null) {
            return;
        }
        try {
            DynamicDataSourceContextHolder.push("erp");
            if (materialId != null) {
                Map<String, Object> material = erpSpareStockQueryMapper.selectMaterial(currentTenantId(), materialId);
                if (material == null) {
                    throw new ServiceException("ERP 物料不存在: materialId=" + materialId);
                }
                Object status = material.get("status");
                if (status != null && !"1".equals(String.valueOf(status))) {
                    throw new ServiceException("ERP 物料已停用, 不可映射: materialId=" + materialId + ", status=" + status);
                }
            }
            if (warehouseId != null) {
                long cnt = erpSpareStockQueryMapper.countEnabledWarehouse(currentTenantId(), warehouseId);
                if (cnt <= 0) {
                    throw new ServiceException("ERP 仓库不存在或已停用: warehouseId=" + warehouseId);
                }
            }
        } finally {
            DynamicDataSourceContextHolder.poll();
        }
    }

    /**
     * 严格模式下断言备件已完成 ERP 映射
     *
     * @param spare 备件视图
     */
    private void assertErpMapping(EqSparePartVo spare) {
        if (!erpStrict) {
            return;
        }
        if (spare.getMaterialId() == null) {
            throw new ServiceException("备件[" + spare.getSpareCode()
                + "]未映射 ERP 物料(material_id), 无法过账到 ERP 仓储; 请先在备件主数据中完成物料映射");
        }
        if (spare.getWarehouseId() == null) {
            throw new ServiceException("备件[" + spare.getSpareCode()
                + "]未指定 ERP 仓库(warehouse_id), 无法过账到 ERP 仓储; 请先在备件主数据中指定仓库");
        }
    }

    /**
     * 校验将要实际投递的历史映射快照，不允许缺仓库后退化为跨仓过账。
     *
     * <p>主数据后来清空或重映射，不改变原过账的目标；历史缺失快照只能先核实原始意图，
     * 不能自动借用当前映射补齐。</p>
     *
     * @param sync 原始同步台账
     */
    private void assertSyncMapping(EqSpareErpSync sync) {
        if (sync.getMaterialId() == null || sync.getWarehouseId() == null) {
            throw new ServiceException("备件原过账映射快照不完整，请核实原始物料和仓库后办理，不可跨仓重投");
        }
    }

    /**
     * 构造过账同步台账
     *
     * @param spare 备件视图
     * @param logEntry 已落库的 MES 库存流水
     * @param type 变动类型
     * @param qty 变动数量
     * @param bo 出入库入参
     * @return 同步台账
     */
    private EqSpareErpSync buildSync(EqSparePartVo spare, EqSpareStockLog logEntry, String type,
                                     BigDecimal qty, EqSpareStockBo bo) {
        String tenantId = currentTenantId();
        EqSpareErpSync sync = new EqSpareErpSync();
        /** 幂等键优先用雪花流水ID; 极少数主键策略未回填时退化为 备件ID+纳秒 兜底(仍唯一) */
        String suffix = logEntry.getLogId() != null
            ? String.valueOf(logEntry.getLogId())
            : spare.getSpareId() + "-" + System.nanoTime();
        sync.setBizKey(BIZ_KEY_PREFIX + tenantId + ":" + suffix);
        sync.setSpareId(spare.getSpareId());
        sync.setSpareCode(spare.getSpareCode());
        sync.setMaterialId(spare.getMaterialId());
        sync.setWarehouseId(spare.getWarehouseId());
        sync.setTransactionType(type);
        sync.setQuantity(qty);
        sync.setRefType(StringUtils.isNotBlank(bo.getRefType()) ? bo.getRefType() : "MANUAL");
        sync.setRefId(bo.getRefId());
        sync.setLogId(logEntry.getLogId());
        sync.setSyncStatus(SYNC_PENDING);
        sync.setRetryCount(0);
        sync.setRemark(bo.getRemark());
        return sync;
    }

    /**
     * 事务提交后投递过账消息；投递失败落 FAILED + 失败原因（不静默）
     *
     * @param sync 同步台账
     */
    private void publishAfterCommit(EqSpareErpSync sync) {
        EqSparePartVo spare = baseMapper.selectVoById(sync.getSpareId());
        Map<String, Object> payload = spare == null ? null : buildStockPayload(spare, sync);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    doPublish(sync, payload);
                }
            });
        } else {
            doPublish(sync, payload);
        }
    }

    /**
     * 投递过账消息并回写同步状态
     *
     * @param sync 同步台账
     * @param payload 消息体
     */
    private void doPublish(EqSpareErpSync sync, Map<String, Object> payload) {
        if (payload == null) {
            markSync(sync, SYNC_FAILED, "备件已被删除, 无法过账到 ERP");
            return;
        }
        try {
            erpMqPublisher.publish(payload);
            markSync(sync, SYNC_SENT, null);
        } catch (Exception e) {
            log.error("备件 ERP 过账消息投递失败(已记账, 待重投): bizKey={}", sync.getBizKey(), e);
            markSync(sync, SYNC_FAILED, e.getMessage());
        }
    }

    /**
     * 构造过账消息体
     *
     * @param spare 备件视图
     * @param sync 同步台账
     * @return 消息体
     */
    private Map<String, Object> buildStockPayload(EqSparePartVo spare, EqSpareErpSync sync) {
        assertSyncMapping(sync);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("bizType", "SPARE_STOCK");
        payload.put("tenantId", currentTenantId());
        payload.put("bizKey", sync.getBizKey());
        payload.put("spareId", spare.getSpareId());
        payload.put("spareCode", spare.getSpareCode());
        payload.put("spareName", spare.getSpareName());
        payload.put("materialId", sync.getMaterialId());
        payload.put("warehouseId", sync.getWarehouseId());
        payload.put("transactionType", sync.getTransactionType());
        payload.put("quantity", sync.getQuantity());
        /**
         * 成本计价(MES-EQ-04 收口): 单价由服务端从 eq_spare_part.unit_cost 读取(此处的 spare 即库中读出的视图),
         * 绝不采信客户端传入的单价; 为空/≤0 时 ERP 侧跳过移动平均并记 WARN
         */
        payload.put("unitCost", spare.getUnitCost());
        payload.put("refType", sync.getRefType());
        payload.put("refId", sync.getRefId());
        payload.put("remark", sync.getRemark());
        return payload;
    }

    /**
     * 回写同步状态
     *
     * @param sync 同步台账
     * @param status 状态
     * @param error 失败原因(可空)
     */
    private void markSync(EqSpareErpSync sync, String status, String error) {
        EqSpareErpSync update = new EqSpareErpSync();
        update.setSyncId(sync.getSyncId());
        update.setSyncStatus(status);
        update.setRetryCount(sync.getRetryCount());
        update.setLastPublishTime(new Date());
        if (error != null) {
            update.setLastError(error.length() > 400 ? error.substring(0, 400) : error);
        }
        erpSyncMapper.updateById(update);
        sync.setSyncStatus(status);
        sync.setLastError(error);
    }

    /**
     * ERP 落账确认后把 ERP 可用量回写到 MES 派生缓存
     *
     * @param sync 同步台账
     */
    private void refreshDerivedStock(EqSpareErpSync sync) {
        Map<Long, BigDecimal> available = queryErpAvailable(List.of(sync.getMaterialId()));
        if (available == null) {
            return;
        }
        BigDecimal value = available.getOrDefault(sync.getMaterialId(), BigDecimal.ZERO);
        EqSparePart update = new EqSparePart();
        update.setSpareId(sync.getSpareId());
        update.setCurrentStock(value);
        baseMapper.update(update, Wrappers.<EqSparePart>lambdaUpdate()
            .eq(EqSparePart::getSpareId, sync.getSpareId())
            .eq(EqSparePart::getMaterialId, sync.getMaterialId())
            .eq(EqSparePart::getWarehouseId, sync.getWarehouseId()));
        EqSpareErpSync syncUpdate = new EqSpareErpSync();
        syncUpdate.setSyncId(sync.getSyncId());
        syncUpdate.setErpAvailableAfter(value);
        erpSyncMapper.updateById(syncUpdate);
    }

    /**
     * 查询 ERP 是否已存在该业务键的过账流水
     *
     * @param bizKey 业务幂等键
     * @return 命中行数
     */
    private long countErpPosting(String bizKey) {
        try {
            DynamicDataSourceContextHolder.push("erp");
            return erpSpareStockQueryMapper.countSparePostingByBizKey(currentTenantId(), bizKey);
        } catch (Exception e) {
            log.error("查询 ERP 过账流水失败: bizKey={}", bizKey, e);
            return 0L;
        } finally {
            DynamicDataSourceContextHolder.poll();
        }
    }

    /**
     * 当前租户ID（无上下文时退化为系统租户，与租户插件默认一致）
     *
     * @return 租户ID
     */
    private String currentTenantId() {
        try {
            String tenantId = TenantHelper.getTenantId();
            return StringUtils.isNotBlank(tenantId) ? tenantId : "000000";
        } catch (Throwable e) {
            /** 无租户上下文(定时/MQ/单测)时 Sa-Token 取用户会抛异常, 退化为系统租户(与租户插件默认一致) */
            return "000000";
        }
    }

    /**
     * 宽松数值转换
     *
     * @param value 原值
     * @return BigDecimal；null 或不可解析时为 0
     */
    private BigDecimal toDecimal(Object value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(String.valueOf(value));
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO;
        }
    }
}

package com.nexus.erp.pu.service;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.tenant.helper.TenantHelper;
import com.nexus.erp.pu.domain.PuRequirementAllocationLedger;
import com.nexus.erp.pu.mapper.PuRequirementCompetitionPoolMapper;
import com.nexus.erp.pu.mapper.PuRequirementCompetitionPoolMapper.RawOrderHead;
import com.nexus.erp.pu.mapper.PuRequirementCompetitionPoolMapper.RawOrderLine;
import com.nexus.erp.pu.mapper.PuRequirementCompetitionPoolMapper.RawRequirementDetail;
import com.nexus.erp.pu.mapper.PuRequirementCompetitionPoolMapper.RawRequirementHead;
import com.nexus.erp.pu.mapper.PuRequirementCompetitionPoolMapper.RawRequisitionHead;
import com.nexus.erp.pu.mapper.PuRequirementCompetitionPoolMapper.RawRequisitionLine;
import com.nexus.erp.pu.mapper.PuRequirementCompetitionPoolMapper.RawStock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;

/**
 * 内部单物料当前读基础，仅返回原始锁定事实，不授予任何采购、额度或来源可信资格。
 *
 * MANDATORY 与 @DS("erp") 本身不证明已有事务属于 ERP；必须由事务资格校验同时核对
 * MyBatis 真实工厂配置、明确 ERP 物理资源及同一已同步连接持有者。已有 master、只读、
 * 未绑定或普通 router-only 事务均在 guard 及来源 Mapper 之前拒绝，不补绑或另借连接。
 * 内部 PuRequirementErpTransactionExecutor 仅建立专属 ERP 物理事务及同连接路由绑定；
 * 本服务不自行建立或修补事务，也不接采购门禁。草稿、申请行与占用的统一业务入口仍未
 * 接线，不因事务基础件而启用正向采购；AI master 与 ERP 不可声称跨库本地 ACID。
 *
 * guard 仅序列化参与该 guard 的路径；实际来源行锁不证明新插入来源、未接 guard 的
 * 库存或订单写路径以及跨库状态安全。后续仍需完整来源、单位、状态、日期、额度、新鲜度、
 * 事务内重读与 CAS 证明，才能交给 allocator。不能把空结果或原始未知值解释为零或完整。
 */
@Service
public class PuRequirementCompetitionPoolReader {

    private static final LocalDate MIN_SQL_DATE = LocalDate.of(1000, 1, 1);
    private static final LocalDate MAX_SQL_DATE = LocalDate.of(9999, 12, 31);

    private final PuRequirementCompetitionPoolMapper mapper;
    private final PuRequirementMaterialLockService materialLockService;
    private final PuRequirementErpTransactionQualification transactionQualification;

    public PuRequirementCompetitionPoolReader(PuRequirementCompetitionPoolMapper mapper,
                                              PuRequirementMaterialLockService materialLockService,
                                              PuRequirementErpTransactionQualification transactionQualification) {
        this.mapper = mapper;
        this.materialLockService = materialLockService;
        this.transactionQualification = transactionQualification;
    }

    /**
     * 先校验上下文，再锁单物料 guard，最后按固定表序和各表主键升序取得原始行当前读。
     * windowEnd 是数据库业务日期的包含式上界；无窗口下界、run 或工单范围过滤。
     * guard 可能确保 guard 行存在，必须先通过严格 ERP 绑定资格；不自行开启或修补事务。
     * 缺失、跨租户或无法辨识的关联头整池拒绝；状态、日期、单位和数量未知则原样保留。
     * 返回值的行锁仅在调用方事务结束前持有；返回不是持久有效的快照或额度证明。
     *
     * @param tenantId 必须与当前租户一致的租户标识
     * @param materialId 当前读所限定的单个物料标识
     * @param windowEnd 数据库业务日期的包含式上界
     * @return 仅供当前事务内使用的原始锁定事实，不代表完整性或采购资格
     */
    @DS("erp")
    @Transactional(propagation = Propagation.MANDATORY)
    public RawLockedFacts materialPool(String tenantId, Long materialId, LocalDate windowEnd) {
        if (!TenantHelper.isEnable() || tenantId == null || tenantId.isBlank()
            || !tenantId.equals(TenantHelper.getTenantId())) {
            throw new ServiceException("物料竞争池租户未启用或与当前租户不匹配");
        }
        transactionQualification.requireWritableErpTransaction();

        if (!positive(materialId)) {
            throw new ServiceException("物料竞争池材料ID必须为正数");
        }
        if (windowEnd == null || windowEnd.isBefore(MIN_SQL_DATE) || windowEnd.isAfter(MAX_SQL_DATE)) {
            throw new ServiceException("物料竞争池结束日期必须是合法SQL业务日期");
        }

        /** 不把 guard 版本当完整性/新鲜度证明；先锁 guard，才允许任何实际来源当前读。 */
        materialLockService.lock(tenantId, List.of(materialId));

        List<RawRequirementDetail> details = materialRows(mapper.lockRequirementDetails(tenantId, materialId, windowEnd),
            tenantId, materialId, RawRequirementDetail::getTenantId, RawRequirementDetail::getMaterialId,
            RawRequirementDetail::getDetailId, "MRP明细");
        List<Long> requirementIds = headIds(details, RawRequirementDetail::getRequirementId, "MRP明细");
        List<RawRequirementHead> requirements = headRows(requirementIds.isEmpty() ? List.of()
            : mapper.lockRequirementHeads(tenantId, requirementIds), requirementIds,
            tenantId, RawRequirementHead::getTenantId, RawRequirementHead::getRequirementId, "MRP头");

        List<RawStock> stocks = materialRows(mapper.lockStocks(tenantId, materialId),
            tenantId, materialId, RawStock::getTenantId, RawStock::getMaterialId, RawStock::getStockId, "库存");

        List<RawOrderLine> orderLines = materialRows(mapper.lockOrderLines(tenantId, materialId),
            tenantId, materialId, RawOrderLine::getTenantId, RawOrderLine::getMaterialId, RawOrderLine::getLineId, "订单行");
        List<Long> orderIds = headIds(orderLines, RawOrderLine::getOrderId, "订单行");
        List<RawOrderHead> orders = headRows(orderIds.isEmpty() ? List.of() : mapper.lockOrderHeads(tenantId, orderIds),
            orderIds, tenantId, RawOrderHead::getTenantId, RawOrderHead::getOrderId, "订单头");

        List<RawRequisitionLine> requisitionLines = materialRows(mapper.lockRequisitionLines(tenantId, materialId),
            tenantId, materialId, RawRequisitionLine::getTenantId, RawRequisitionLine::getMaterialId,
            RawRequisitionLine::getLineId, "申请行");
        List<Long> requisitionIds = headIds(requisitionLines, RawRequisitionLine::getRequisitionId, "申请行");
        List<RawRequisitionHead> requisitions = headRows(requisitionIds.isEmpty() ? List.of()
            : mapper.lockRequisitionHeads(tenantId, requisitionIds), requisitionIds,
            tenantId, RawRequisitionHead::getTenantId, RawRequisitionHead::getRequisitionId, "申请头");

        List<PuRequirementAllocationLedger> allocations = materialRows(mapper.lockAllocations(tenantId, materialId),
            tenantId, materialId, PuRequirementAllocationLedger::getTenantId, PuRequirementAllocationLedger::getMaterialId,
            PuRequirementAllocationLedger::getAllocationId, "占用账本");

        return new RawLockedFacts(details, requirements, stocks, orderLines, orders,
            requisitionLines, requisitions, allocations);
    }

    /** 仅检查结构/作用域身份，不推断数量、日期、单位、来源类型或业务状态。 */
    private static <T> List<T> materialRows(List<T> rows, String tenantId, Long materialId,
                                           Function<T, String> tenant, Function<T, Long> material,
                                           Function<T, Long> id, String source) {
        List<T> raw = rawRows(rows, source);
        Set<Long> identities = new HashSet<>();
        for (T row : raw) {
            Long rowId = id.apply(row);
            if (!tenantId.equals(tenant.apply(row)) || !materialId.equals(material.apply(row))
                || !positive(rowId) || !identities.add(rowId)) {
                throw new ServiceException(source + "身份未知、重复或不属于当前租户物料，拒绝整池");
            }
        }
        return raw;
    }

    private static <T> List<Long> headIds(List<T> rows, Function<T, Long> reference, String source) {
        Set<Long> ids = new TreeSet<>();
        for (T row : rows) {
            Long headId = reference.apply(row);
            if (!positive(headId)) {
                throw new ServiceException(source + "关联头ID未知，拒绝整池");
            }
            ids.add(headId);
        }
        return List.copyOf(ids);
    }

    private static <T> List<T> headRows(List<T> rows, List<Long> requestedIds, String tenantId,
                                       Function<T, String> tenant, Function<T, Long> id, String source) {
        List<T> raw = rawRows(rows, source);
        Set<Long> expected = new HashSet<>(requestedIds);
        Set<Long> actual = new HashSet<>();
        for (T row : raw) {
            Long headId = id.apply(row);
            if (!tenantId.equals(tenant.apply(row)) || !expected.contains(headId) || !actual.add(headId)) {
                throw new ServiceException(source + "租户或引用身份未知/不一致，拒绝整池");
            }
        }
        if (!actual.equals(expected)) {
            /** 显式 tenant SQL 不读外租户头；引用不到同租户头不是可忽略的零来源。 */
            throw new ServiceException(source + "缺失、跨租户或无法核实，拒绝整池");
        }
        return raw;
    }

    private static <T> List<T> rawRows(List<T> rows, String source) {
        if (rows == null || rows.stream().anyMatch(row -> row == null)) {
            throw new ServiceException(source + "当前读结果未知，拒绝整池");
        }
        return List.copyOf(rows);
    }

    private static boolean positive(Long value) {
        return value != null && value > 0;
    }

    /**
     * 原始行容器，没有 complete/verified 或额度资格标志，也不是 controller 输出契约。
     * 仅列表结构不可修改；原始投影、Date/实体字段仍为未解释的事实，不能跨事务当新鲜快照。
     */
    public record RawLockedFacts(List<RawRequirementDetail> requirementDetails,
                                 List<RawRequirementHead> requirementHeads,
                                 List<RawStock> stocks,
                                 List<RawOrderLine> orderLines,
                                 List<RawOrderHead> orderHeads,
                                 List<RawRequisitionLine> requisitionLines,
                                 List<RawRequisitionHead> requisitionHeads,
                                 List<PuRequirementAllocationLedger> allocations) {
        public RawLockedFacts {
            requirementDetails = List.copyOf(requirementDetails);
            requirementHeads = List.copyOf(requirementHeads);
            stocks = List.copyOf(stocks);
            orderLines = List.copyOf(orderLines);
            orderHeads = List.copyOf(orderHeads);
            requisitionLines = List.copyOf(requisitionLines);
            requisitionHeads = List.copyOf(requisitionHeads);
            allocations = List.copyOf(allocations);
        }
    }
}

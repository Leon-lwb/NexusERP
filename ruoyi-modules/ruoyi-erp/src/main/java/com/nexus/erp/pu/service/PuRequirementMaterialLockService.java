package com.nexus.erp.pu.service;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.tenant.helper.TenantHelper;
import com.nexus.erp.pu.mapper.PuRequirementMaterialGuardMapper;
import com.nexus.erp.ai.adapter.ErpPurchaseSourceSchemaVerifier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeSet;

/**
 * ERP 同租户物料竞争池的有界行锁；不代表库存、需求或订单已经被锁定。
 * 上层必须从 ERP 数据源的代理事务入口调用本服务代理；这里不自启事务，也不授权业务操作。
 * 无登录后台任务须由上层显式使用 TenantHelper.dynamic 设定租户；各入口自行校验身份和权限。
 */
@Service
public class PuRequirementMaterialLockService {

    private final PuRequirementMaterialGuardMapper guardMapper;
    private final ErpPurchaseSourceSchemaVerifier schemaVerifier;

    public PuRequirementMaterialLockService(PuRequirementMaterialGuardMapper guardMapper) {
        this.guardMapper = guardMapper;
        this.schemaVerifier = null;
    }

    @Autowired
    public PuRequirementMaterialLockService(PuRequirementMaterialGuardMapper guardMapper,
                                            ErpPurchaseSourceSchemaVerifier schemaVerifier) {
        this.guardMapper = java.util.Objects.requireNonNull(guardMapper);
        this.schemaVerifier = java.util.Objects.requireNonNull(schemaVerifier);
    }

    /**
     * 在已有 ERP 事务中逐行先确保 guard 存在，再按升序取得当前读行锁。
     * 锁由上层事务持有直至提交或回滚；同租户并发序列化需在迁移后经数据库验证。
     *
     * @param tenantId 必须与当前租户一致的租户标识
     * @param materialIds 待锁定的物料标识集合
     * @return 已锁材料的版本（键按材料 ID 升序排列）；不推进版本，也不执行其他业务写入
     */
    @DS("erp")
    @Transactional(propagation = Propagation.MANDATORY)
    public Map<Long, Integer> lock(String tenantId, Collection<Long> materialIds) {
        return lockInternal(tenantId, materialIds, true);
    }

    /** 永久恢复查询只锁既有guard，不ensure或补建；缺失、未知版本均拒绝。 */
    @DS("erp")
    @Transactional(propagation = Propagation.MANDATORY)
    public Map<Long, Integer> lockExisting(String tenantId, Collection<Long> materialIds) {
        return lockInternal(tenantId, materialIds, false);
    }

    private Map<Long, Integer> lockInternal(String tenantId, Collection<Long> materialIds, boolean provision) {
        if (!TenantHelper.isEnable() || tenantId == null || tenantId.isBlank()
            || !tenantId.equals(TenantHelper.getTenantId())) {
            throw new ServiceException("物料竞争池租户未启用或与当前租户不匹配");
        }
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new ServiceException("物料竞争池锁必须加入已有 ERP 事务");
        }
        if (materialIds == null || materialIds.isEmpty()) {
            throw new ServiceException("物料竞争池材料ID不能为空");
        }
        for (Long materialId : materialIds) {
            if (materialId == null || materialId <= 0) {
                throw new ServiceException("物料竞争池材料ID必须为正数");
            }
        }

        // 位于真正 INSERT 边界，直接调用锁服务也不能绕开物理归属、表定义及事务引擎认证。
        if (schemaVerifier == null) {
            throw new ServiceException("物料竞争池锁缺少事务来源认证依赖");
        }
        schemaVerifier.requireTransactionalSources();

        Map<Long, Integer> versions = new LinkedHashMap<>();
        for (Long materialId : new TreeSet<>(materialIds)) {
            /** INSERT IGNORE 返回 0 可能表示 guard 已存在；必须继续 SELECT ... FOR UPDATE。 */
            if (provision) guardMapper.ensureGuard(tenantId, materialId);
            Integer version = guardMapper.lockVersion(tenantId, materialId);
            if (version == null || (!provision && (version < 0 || version == Integer.MAX_VALUE))) {
                throw new ServiceException("物料竞争池行不存在或版本不可核验");
            }
            versions.put(materialId, version);
        }
        return Collections.unmodifiableMap(versions);
    }
}

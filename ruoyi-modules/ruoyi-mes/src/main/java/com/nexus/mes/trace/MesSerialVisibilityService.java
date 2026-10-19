package com.nexus.mes.trace;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.nexus.common.trace.port.SerialVisibilityPort;
import com.nexus.mes.pro.domain.ProSn;
import com.nexus.mes.pro.mapper.ProSnMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

/**
 * 通过现有制造映射及数据权限范围求候选交集，并显式限制当前租户。
 */
@Service
@DS("mes")
@RequiredArgsConstructor
public class MesSerialVisibilityService implements SerialVisibilityPort {
    private final ProSnMapper mapper;
    private final MesTraceContext context;

    @Override
    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    public Set<Long> visibleIdentities(String tenant, Set<Long> identityIds) {
        return Set.copyOf(scopedMappings(tenant, identityIds).keySet());
    }

    @Override
    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    public Map<Long, Long> localSnIds(String tenant, Set<Long> identityIds) {
        return scopedMappings(tenant, identityIds);
    }

    private Map<Long, Long> scopedMappings(String tenant, Set<Long> identityIds) {
        context.requireTenant(tenant);
        if (identityIds == null || identityIds.isEmpty()) {
            return Map.of();
        }
        var ids = identityIds.stream().filter(id -> id != null && id > 0).sorted().toList();
        var mappings = new HashMap<Long, Long>();
        for (int start = 0; start < ids.size(); start += 500) {
            var batch = ids.subList(start, Math.min(start + 500, ids.size()));
            var rows =
                    mapper.selectList(
                            Wrappers.<ProSn>lambdaQuery()
                                    .eq(ProSn::getTenantId, tenant)
                                    .eq(ProSn::getDelFlag, "0")
                                    .in(ProSn::getIdentityId, batch));
            for (var row : rows) {
                if (!tenant.equals(row.getTenantId())
                        || !batch.contains(row.getIdentityId())
                        || !"0".equals(row.getDelFlag())) {
                    continue;
                }
                MesTraceContext.need(row.getSnId() != null && row.getSnId() > 0, "MES身份映射缺少真实本地SN");
                Long prior = mappings.putIfAbsent(row.getIdentityId(), row.getSnId());
                MesTraceContext.need(
                        prior == null || prior.equals(row.getSnId()), "MES正式身份存在多个本地SN映射，需消除歧义");
            }
        }
        return Map.copyOf(mappings);
    }
}

package com.nexus.mes.flash.provider;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.mes.flash.domain.MesResourceItem;
import com.nexus.mes.flash.domain.MesResourcePool;
import com.nexus.mes.flash.domain.bo.FlashRecordBo;
import com.nexus.mes.flash.mapper.MesResourcePoolMapper;
import com.nexus.mes.flash.service.IMesResourcePoolService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * MAC 刷写 Provider (source=POOL)
 * <p>从 MAC 资源池原子分配一个 MAC 给 SN(一SN绑一MAC), 校验资源已绑定。
 * 池选择: 按 resource_type=MAC 的启用池(首个)。</p>
 *
 * @author Nexus
 */
@Component
@RequiredArgsConstructor
public class MacPoolFlashProvider implements FlashTypeProvider {

    private final IMesResourcePoolService resourcePoolService;
    private final MesResourcePoolMapper resourcePoolMapper;

    @Override
    public String getTypeCode() {
        return "mac";
    }

    @Override
    public String getSourceType() {
        return "POOL";
    }

    @Override
    public String getDisplayName() {
        return "刷写MAC";
    }

    @Override
    public FlashContent resolveContent(FlashContext ctx) {
        Long poolId = resolvePoolId("MAC");
        MesResourceItem item = resourcePoolService.allocate(poolId, ctx.getSnId(), ctx.getSnCode());
        return FlashContent.builder()
            .contentRefType("MAC_POOL")
            .contentRefId(item.getItemId())
            .contentPayload("{\"mac\":\"" + item.getResourceValue() + "\"}")
            .build();
    }

    @Override
    public FlashVerifyResult verify(FlashContext ctx, FlashContent expected, FlashRecordBo actual) {
        // POOL类: 资源已分配(ALLOCATED)即视为可刷写; 实际结果以上报 result 为准
        return FlashVerifyResult.builder()
            .pass(true)
            .contentRefType(expected.getContentRefType())
            .contentPayload(expected.getContentPayload())
            .build();
    }

    private Long resolvePoolId(String resourceType) {
        MesResourcePool pool = resourcePoolMapper.selectOne(Wrappers.<MesResourcePool>lambdaQuery()
            .eq(MesResourcePool::getResourceType, resourceType)
            .eq(MesResourcePool::getStatus, "ENABLED")
            .last("LIMIT 1"));
        if (pool == null) {
            throw new ServiceException("无启用的" + resourceType + "资源池, 请先创建并导入资源");
        }
        return pool.getPoolId();
    }
}

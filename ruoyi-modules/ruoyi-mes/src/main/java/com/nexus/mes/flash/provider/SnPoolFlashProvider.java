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
 * SN 刷写 Provider (source=POOL)
 * <p>从 SN 资源池原子分配一个 SN 资源给目标 SN(一SN绑一SN资源)。</p>
 *
 * @author Nexus
 */
@Component
@RequiredArgsConstructor
public class SnPoolFlashProvider implements FlashTypeProvider {

    private final IMesResourcePoolService resourcePoolService;
    private final MesResourcePoolMapper resourcePoolMapper;

    @Override
    public String getTypeCode() {
        return "sn";
    }

    @Override
    public String getSourceType() {
        return "POOL";
    }

    @Override
    public String getDisplayName() {
        return "刷写SN";
    }

    @Override
    public FlashContent resolveContent(FlashContext ctx) {
        Long poolId = resolvePoolId("SN");
        MesResourceItem item = resourcePoolService.allocate(poolId, ctx.getSnId(), ctx.getSnCode());
        return FlashContent.builder()
            .contentRefType("SN_POOL")
            .contentRefId(item.getItemId())
            .contentPayload("{\"sn\":\"" + item.getResourceValue() + "\"}")
            .build();
    }

    @Override
    public FlashVerifyResult verify(FlashContext ctx, FlashContent expected, FlashRecordBo actual) {
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

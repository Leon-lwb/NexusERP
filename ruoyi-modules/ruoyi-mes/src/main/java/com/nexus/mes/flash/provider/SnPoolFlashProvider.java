package com.nexus.mes.flash.provider;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.core.utils.StringUtils;
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
    public String getPoolResourceType() {
        return "SN";
    }

    @Override
    public FlashContent resolveContent(FlashContext ctx) {
        Long poolId = resolvePoolId("SN", ctx.getContentRefId());
        MesResourceItem item = resourcePoolService.allocate(poolId, ctx.getSnId(), ctx.getSnCode());
        return FlashContent.builder()
            .contentRefType("SN_POOL")
            // MES-FLASH-03: 同 MacPoolFlashProvider, content_ref_id 落"工序配置的内容源ID"(门禁比对口径)
            .contentRefId(ctx.getContentRefId() != null ? ctx.getContentRefId() : item.getItemId())
            .contentPayload(PoolFlashPayload.of("sn", item.getResourceValue()))
            .build();
    }

    @Override
    public FlashVerifyResult verify(FlashContext ctx, FlashContent expected, FlashRecordBo actual) {
        // POOL类: 资源已分配; SN在制由 confirmFlash(buildValidatedRecord) 前置统一校验
        String expectedValue = PoolFlashPayload.extract(expected.getContentPayload(), "sn");
        String actualPayload = actual == null ? null : actual.getContentPayload();
        String actualValue = PoolFlashPayload.extract(actualPayload, "sn");
        FlashVerifyResult.FlashVerifyResultBuilder builder = FlashVerifyResult.builder()
            .pass(true)
            .contentRefType(expected.getContentRefType());
        if (StringUtils.isNotBlank(actualPayload)) {
            if (StringUtils.isBlank(actualValue)) {
                builder.pass(false)
                    .failReason("设备上报实刷内容缺少 sn 字段: " + actualPayload)
                    .contentPayload(expected.getContentPayload());
            } else {
                // MES-FLASH-02: 落库实际烧录值
                builder.contentPayload(PoolFlashPayload.of("sn", actualValue));
                if (expectedValue != null && !expectedValue.equalsIgnoreCase(actualValue)) {
                    builder.pass(false)
                        .failReason("SN实刷值与池分配值不一致: 应刷=" + expectedValue + " 实际=" + actualValue);
                }
            }
        } else {
            builder.contentPayload(expected.getContentPayload());
        }
        return builder.build();
    }

    /**
     * 池选择(MES-FLASH-06): 优先 contentRefId 指向的启用池, 退化取首个启用池并强制 ORDER BY(create_time, pool_id)。
     */
    private Long resolvePoolId(String resourceType, Long preferredPoolId) {
        if (preferredPoolId != null) {
            MesResourcePool preferred = resourcePoolMapper.selectById(preferredPoolId);
            if (preferred != null && resourceType.equals(preferred.getResourceType())
                && "ENABLED".equals(preferred.getStatus())) {
                return preferred.getPoolId();
            }
        }
        MesResourcePool pool = resourcePoolMapper.selectOne(Wrappers.<MesResourcePool>lambdaQuery()
            .eq(MesResourcePool::getResourceType, resourceType)
            .eq(MesResourcePool::getStatus, "ENABLED")
            .orderByAsc(MesResourcePool::getCreateTime)
            .orderByAsc(MesResourcePool::getPoolId)
            .last("LIMIT 1"));
        if (pool == null) {
            throw new ServiceException("无启用的" + resourceType + "资源池, 请先创建并导入资源");
        }
        return pool.getPoolId();
    }
}

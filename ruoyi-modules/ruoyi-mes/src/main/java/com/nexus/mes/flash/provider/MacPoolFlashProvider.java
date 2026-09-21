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
    public String getPoolResourceType() {
        return "MAC";
    }

    @Override
    public FlashContent resolveContent(FlashContext ctx) {
        Long poolId = resolvePoolId("MAC", ctx.getContentRefId());
        MesResourceItem item = resourcePoolService.allocate(poolId, ctx.getSnId(), ctx.getSnCode());
        return FlashContent.builder()
            .contentRefType("MAC_POOL")
            // MES-FLASH-03: content_ref_id 语义统一为"工序配置的内容源ID"(与 ProFlowCardServiceImpl 门禁比对口径一致),
            // 否则落资源项ID会导致门禁按配置值反查永远匹配不到、工单卡死; 工序未配置 contentRefId 时退回资源项ID留痕
            .contentRefId(ctx.getContentRefId() != null ? ctx.getContentRefId() : item.getItemId())
            .contentPayload(PoolFlashPayload.of("mac", item.getResourceValue()))
            .build();
    }

    @Override
    public FlashVerifyResult verify(FlashContext ctx, FlashContent expected, FlashRecordBo actual) {
        // POOL类: 资源已分配(ALLOCATED)即视为可刷写; SN在制由 confirmFlash(buildValidatedRecord) 前置统一校验(所有类型)
        String expectedValue = PoolFlashPayload.extract(expected.getContentPayload(), "mac");
        String actualPayload = actual == null ? null : actual.getContentPayload();
        String actualValue = PoolFlashPayload.extract(actualPayload, "mac");
        FlashVerifyResult.FlashVerifyResultBuilder builder = FlashVerifyResult.builder()
            .pass(true)
            .contentRefType(expected.getContentRefType());
        if (StringUtils.isNotBlank(actualPayload)) {
            if (StringUtils.isBlank(actualValue)) {
                // 上报了实刷内容但缺 mac 字段: fail-closed, 不落成 PASS
                builder.pass(false)
                    .failReason("设备上报实刷内容缺少 mac 字段: " + actualPayload)
                    .contentPayload(expected.getContentPayload());
            } else {
                // MES-FLASH-02: 落库"实际烧录值"(期望值仍保留在 content_ref_id 指向的资源项, 供比对追溯)
                builder.contentPayload(PoolFlashPayload.of("mac", actualValue));
                if (expectedValue != null && !expectedValue.equalsIgnoreCase(actualValue)) {
                    builder.pass(false)
                        .failReason("MAC实刷值与池分配值不一致: 应刷=" + expectedValue + " 实际=" + actualValue);
                }
            }
        } else {
            // 设备未上报实刷值(旧客户端): 退回池分配值, 保持记录可用
            builder.contentPayload(expected.getContentPayload());
        }
        return builder.build();
    }

    /**
     * 池选择(MES-FLASH-06):
     * <ol>
     *   <li>优先用工序配置的 contentRefId 作为池ID(其确实指向该类型的启用池时), 让"工单绑定哪个池"可控;</li>
     *   <li>否则退化取该类型首个启用池 —— 必须带 ORDER BY(create_time, pool_id), 消除无排序 LIMIT 1 的随机选择。</li>
     * </ol>
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

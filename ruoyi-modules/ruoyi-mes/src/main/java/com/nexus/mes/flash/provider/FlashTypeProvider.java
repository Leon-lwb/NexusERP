package com.nexus.mes.flash.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.nexus.mes.flash.domain.bo.FlashRecordBo;

/**
 * 刷写类型执行器接口(仿 ruoyi-flow FlowNodeProvider)
 * <p>每类刷写(固件/配置/MAC/SN/外部REST)一个实现, 注册进 {@link FlashTypeRegistry}。
 * 刷写确认/上报时按 flash_type_code 路由到对应 Provider 解析应刷内容与校验结果。</p>
 *
 * @author Nexus
 */
public interface FlashTypeProvider {

    /** 刷写类型标识: firmware_file/config_file/mac/sn/external_* */
    String getTypeCode();

    /** 内容来源: FILE/POOL/EXTERNAL/INLINE */
    String getSourceType();

    /** 显示名(前端) */
    String getDisplayName();

    /** 来源配置schema(前端表单驱动, 仿 NodeParamSchema); P0 可返回 null */
    default JsonNode getSourceConfigSchema() {
        return null;
    }

    /**
     * 解析该刷写类型在上下文中的"应刷内容"
     * <p>固件类: 按 contentRefId 查 mes_firmware 得 version/md5;
     * 池类: 由 allocate 分配的资源</p>
     */
    FlashContent resolveContent(FlashContext ctx);

    /**
     * 校验产线上报的实际刷写结果, 返回校验结果(含回填到记录的内容字段)
     * <p>固件类: RELEASED + MD5匹配; 池类: 资源已绑定</p>
     */
    FlashVerifyResult verify(FlashContext ctx, FlashContent expected, FlashRecordBo actual);

    /** 资源分配(POOL/EXTERNAL类, 一SN绑一资源); FILE/INLINE类默认返回 null */
    default ResourceAllocation allocate(FlashContext ctx) {
        return null;
    }

    /**
     * 池类资源类型(对应 mes_resource_pool.resource_type, 如 MAC/SN); 非池类返回 null。
     * <p>MES-FLASH-06: 刷写结果回写资源项状态(PASS 置 USED / FAIL 回收 AVAILABLE)需要按资源类型定位池,
     * 故由池类 Provider 声明; 非池类返回 null 即不参与资源回收。</p>
     */
    default String getPoolResourceType() {
        return null;
    }
}

package com.nexus.mes.flash.provider;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 资源分配结果(POOL/EXTERNAL 类 Provider.allocate 返回)
 *
 * @author Nexus
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResourceAllocation {

    /** 分配的资源项ID(mes_resource_item_id) */
    private Long contentRefId;

    /** 资源内容JSON(如 {"mac":"00:1A:2B:3C:4D:5E"}) */
    private String contentPayload;
}

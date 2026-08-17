package com.nexus.mes.flash.provider;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 刷写校验结果(由 Provider.verify 返回, 含回填到记录的内容字段)
 *
 * @author Nexus
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FlashVerifyResult {

    private boolean pass;
    private String failReason;

    /** 回填到 FlashRecord 的内容字段 */
    private String contentRefType;
    private String contentVersion;
    private String contentChecksum;
    private String contentPayload;
}

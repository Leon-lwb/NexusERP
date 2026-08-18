package com.nexus.mes.flash.provider;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 应刷内容(由 Provider.resolveContent 解析)
 *
 * @author Nexus
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FlashContent {

    /** 内容引用类型: FIRMWARE/CONFIG/MAC_POOL/SN_POOL/EXTERNAL */
    private String contentRefType;

    /** 内容源记录ID(固件=mes_firmware_id, 池=mes_resource_item_id) */
    private Long contentRefId;

    /** 内容版本(固件类=固件版本) */
    private String contentVersion;

    /** 内容校验值(固件类=MD5) */
    private String contentChecksum;

    /** 实际写入内容JSON(池类, 如 {"mac":"00:1A:2B:3C:4D:5E"}) */
    private String contentPayload;
}

package com.nexus.mes.flash.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.nexus.common.tenant.core.TenantEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 刷写类型定义对象 mes_flash_type
 * <p>配置化刷写类型目录: 固件/配置/MAC/SN/外部REST, 每类对应一个 FlashTypeProvider bean</p>
 *
 * @author Nexus
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mes_flash_type")
public class MesFlashType extends TenantEntity {

    /** 刷写类型ID(主键) */
    @TableId(value = "flash_type_id")
    private Long flashTypeId;

    /** 类型标识: firmware_file/config_file/mac/sn/external_* */
    private String typeCode;

    /** 显示名 */
    private String typeName;

    /** 内容来源: FILE/POOL/EXTERNAL/INLINE */
    private String sourceType;

    /** SPI Provider bean名(如 firmwareFileFlashProvider) */
    private String sourceProvider;

    /** 来源配置JSON */
    private String sourceConfig;

    /** 校验策略: MD5_MATCH/RESOURCE_BOUND/CUSTOM */
    private String verifyStrategy;

    /** 是否参与完工门禁(0否/1是) */
    private String gateEnabled;

    /** ENABLED/DISABLED */
    private String status;

    /** 排序 */
    private Integer sortOrder;

    /** 备注 */
    private String remark;

    /** 删除标志 */
    @TableLogic
    private String delFlag;
}

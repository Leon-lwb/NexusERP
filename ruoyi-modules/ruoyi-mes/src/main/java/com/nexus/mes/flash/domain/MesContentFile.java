package com.nexus.mes.flash.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.nexus.common.tenant.core.TenantEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Date;

/**
 * 刷写内容档案对象 mes_content_file
 * <p>配置文件等 FILE 类刷写内容源(审批发布, 复用固件范式)。</p>
 *
 * @author Nexus
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mes_content_file")
public class MesContentFile extends TenantEntity {

    @TableId(value = "content_file_id")
    private Long contentFileId;

    /** 配置编码(MES_CONFIG_CODE) */
    private String contentCode;
    private String contentName;
    /** CONFIG_FILE(可扩展校准/证书) */
    private String contentType;
    private Long materialId;
    private String materialCode;
    private String materialName;
    private String version;
    private Long ossId;
    private String fileMd5;
    private Long fileSize;
    private String releaseNote;
    /** DRAFT/IN_REVIEW/RELEASED/ARCHIVED */
    private String status;
    /** 物料默认版本(0否/1是) */
    private String isDefault;
    private Long publishedBy;
    private Date publishTime;
    private String remark;
    @TableLogic
    private String delFlag;
}

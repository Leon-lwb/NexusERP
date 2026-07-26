package com.nexus.mes.sop.domain.bo;

import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import com.nexus.common.mybatis.core.domain.BaseEntity;
import com.nexus.mes.sop.domain.SopVersion;

/**
 * SOP版本业务对象 sop_version
 * <p>承载 版本 的增改入参与查询条件。addVersion 复用此 BO。</p>
 *
 * @author Nexus
 */
@Data
@EqualsAndHashCode(callSuper = true)
@AutoMapper(target = SopVersion.class, reverseConvertGenerate = false)
public class SopVersionBo extends BaseEntity {

    /** 版本ID */
    private Long versionId;

    /** SOP ID */
    @NotNull(message = "SOP ID不能为空")
    private Long sopId;

    /** 版本号(留空则自动递增 1.0/1.1/1.2...) */
    private String versionNo;

    /** 该版本文件OSS ID */
    @NotNull(message = "文件不能为空")
    private String fileOssId;

    /** 文件类型: PDF/IMAGE/VIDEO/WORD */
    private String fileType;

    /** 变更说明 */
    private String changeLog;

    /** 状态(查询用) */
    private String status;

    /** 备注 */
    private String remark;

    /** 批量删除ID集合 */
    private Long[] ids;
}

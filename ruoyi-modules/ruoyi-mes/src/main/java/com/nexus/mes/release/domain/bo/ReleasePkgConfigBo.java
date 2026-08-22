package com.nexus.mes.release.domain.bo;

import com.nexus.mes.release.domain.ReleasePkgConfig;
import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * MES发布包配置项业务对象 mes_release_pkg_config
 *
 * @author Nexus
 */
@Data
@AutoMapper(target = ReleasePkgConfig.class, reverseConvertGenerate = false)
public class ReleasePkgConfigBo {

    /** 主键ID(保存时忽略, 先删后插) */
    private Long configId;

    /** 配置分组: FLASH/TEST_PARAM/LABEL/PACKAGING/PROCESS */
    @NotBlank(message = "配置分组不能为空")
    private String configGroup;

    /** 配置键 */
    @NotBlank(message = "配置键不能为空")
    private String configKey;

    /** 配置值 */
    private String configValue;

    /** 值类型: STRING/NUMBER/BOOLEAN/JSON */
    private String configType;

    /** 关联工序ID(空=全局生效) */
    private Long processId;

    /** 工序编码(冗余) */
    private String processCode;

    /** 排序 */
    private Integer sortOrder;

    /** 备注 */
    private String remark;
}

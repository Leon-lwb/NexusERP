package com.nexus.mes.release.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import com.nexus.mes.release.domain.ReleasePkgConfig;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * MES发布包配置项视图对象 mes_release_pkg_config
 *
 * @author Nexus
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = ReleasePkgConfig.class)
public class ReleasePkgConfigVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键ID */
    private Long configId;

    /** 发布包ID */
    private Long packageId;

    /** 配置分组: FLASH/TEST_PARAM/LABEL/PACKAGING/PROCESS */
    @ExcelProperty("配置分组")
    private String configGroup;

    /** 配置键 */
    @ExcelProperty("配置键")
    private String configKey;

    /** 配置值 */
    @ExcelProperty("配置值")
    private String configValue;

    /** 值类型: STRING/NUMBER/BOOLEAN/JSON */
    private String configType;

    /** 关联工序ID(空=全局生效) */
    private Long processId;

    /** 工序编码 */
    private String processCode;

    /** 排序 */
    private Integer sortOrder;

    /** 备注 */
    private String remark;
}

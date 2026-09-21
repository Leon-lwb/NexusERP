package com.nexus.mes.dhr.domain.vo;

import com.nexus.mes.dhr.domain.DhrSection;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * DHR 分册视图对象（字段与 dhr_section 列一一对应，驼峰）。
 *
 * @author Nexus
 */
@Data
@AutoMapper(target = DhrSection.class)
public class DhrSectionVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 分册主键 */
    private Long sectionId;

    /** 所属 DHR 主键 */
    private Long dhrId;

    /** 分册编码 */
    private String sectionCode;

    /** 分册名称 */
    private String sectionName;

    /** 条目数 */
    private Integer itemCount;

    /** 分册条目 JSON */
    private String contentJson;

    /** 正文哈希 */
    private String contentHash;

    /** 排序号 */
    private Integer sortOrder;
}

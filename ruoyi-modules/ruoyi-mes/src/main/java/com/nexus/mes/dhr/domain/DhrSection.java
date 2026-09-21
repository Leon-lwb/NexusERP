package com.nexus.mes.dhr.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

/**
 * DHR 批记录分册对象 dhr_section。
 *
 * <p><b>append-only</b>：冻结（提交）时写入，进入 SUBMITTED 后不再有任何改写路径。
 * DRAFT 期可被 rebuild 覆盖（此时尚未冻结，不构成审计台账）。</p>
 *
 * <p>本表只有 {@code create_time}，<b>没有</b> create_by/update_by/update_time/del_flag，
 * 因此不继承 {@code TenantEntity}（继承会把不存在的列写进 INSERT）。租户列显式声明并由
 * 服务层写入；多租户插件在 INSERT 时检测到列已存在会跳过，不会重复添加。</p>
 *
 * @author Nexus
 */
@Data
@TableName("dhr_section")
public class DhrSection implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 分册主键 */
    @TableId(value = "section_id")
    private Long sectionId;

    /** 租户编号 */
    private String tenantId;

    /** 所属 DHR 主表ID */
    private Long dhrId;

    /** 分册编码，见 {@link DhrSectionCode} */
    private String sectionCode;

    /** 分册名称 */
    private String sectionName;

    /** 条目数 */
    private Integer itemCount;

    /** 分册条目 JSON（List<Map> 序列化） */
    private String contentJson;

    /** 正文哈希 = SM3(content_json) */
    private String contentHash;

    /** 排序号（1 基，与 {@link DhrSectionCode} 声明顺序一致） */
    private Integer sortOrder;

    /** 写入时间 */
    private Date createTime;
}

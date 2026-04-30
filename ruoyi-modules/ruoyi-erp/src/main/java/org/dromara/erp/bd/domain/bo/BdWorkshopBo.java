package org.dromara.erp.bd.domain.bo;

import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.dromara.common.mybatis.core.domain.BaseEntity;
import org.dromara.erp.bd.domain.BdWorkshop;

import java.math.BigDecimal;

/**
 * 车间设置业务对象 bd_workshop
 *
 * @author ruoyi
 * @date 2025-11-18
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@AutoMapper(target = BdWorkshop.class, reverseConvertGenerate = false)
public class BdWorkshopBo extends BaseEntity {

    /**
     * 车间ID
     */
    private Long workshopId;

    /**
     * 车间编码
     */
    @NotBlank(message = "车间编码不能为空")
    private String workshopCode;

    /**
     * 车间名称
     */
    @NotBlank(message = "车间名称不能为空")
    private String workshopName;

    /**
     * 车间描述
     */
    private String workshopDesc;

    /**
     * 车间主任ID
     */
    private Long managerId;

    /**
     * 车间主任
     */
    private String managerName;

    /**
     * 产能（标准工时/天）
     */
    private BigDecimal capacity;

    /**
     * 状态
     */
    private Long status;

    /**
     * 排序
     */
    private Long sortOrder;

    /**
     * 预留字段1
     */
    private String attr1;

    /**
     * 预留字段2
     */
    private String attr2;

    /**
     * 预留字段3
     */
    private Long attr3;

    /**
     * 预留字段4
     */
    private Long attr4;

    /**
     * 批量删除ID集合
     */
    private Long[] ids;

}

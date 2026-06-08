package com.nexus.erp.wm.domain.bo;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 批量生成库位业务对象
 * <p>
 * 用于按"行×列×层"规则批量生成库位，适用于货架型库区。
 *
 * @author nexus
 */
@Data
@NoArgsConstructor
public class WmLocationBatchGenerateBo {

    /**
     * 库区ID
     */
    @NotNull(message = "库区ID不能为空")
    private Long areaId;

    /**
     * 起始行号
     */
    @NotNull(message = "起始行号不能为空")
    @Min(value = 1, message = "起始行号最小为1")
    private Integer rowStart;

    /**
     * 结束行号
     */
    @NotNull(message = "结束行号不能为空")
    @Min(value = 1, message = "结束行号最小为1")
    @Max(value = 999, message = "结束行号最大为999")
    private Integer rowEnd;

    /**
     * 列数
     */
    @NotNull(message = "列数不能为空")
    @Min(value = 1, message = "列数最小为1")
    @Max(value = 999, message = "列数最大为999")
    private Integer colCount;

    /**
     * 层数
     */
    @NotNull(message = "层数不能为空")
    @Min(value = 1, message = "层数最小为1")
    @Max(value = 999, message = "层数最大为999")
    private Integer layerCount;

    /**
     * 编码前缀（留空则使用库区编码）
     */
    private String codePrefix;

    /**
     * 库位类型
     */
    private String locationType;
}

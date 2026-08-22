package com.nexus.mes.release.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import com.nexus.common.translation.annotation.Translation;
import com.nexus.common.translation.constant.TransConstant;
import com.nexus.mes.release.domain.ReleasePackage;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;
import java.util.List;

/**
 * MES客户定制化发布包视图对象 mes_release_package
 *
 * @author Nexus
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = ReleasePackage.class)
public class ReleasePackageVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 发布包ID */
    private Long packageId;

    /** 发布包编码 */
    @ExcelProperty("发布包编码")
    private String packageCode;

    /** 客户ID */
    private Long clientId;

    /** 客户名称 */
    @ExcelProperty("客户名称")
    private String clientName;

    /** 成品物料ID */
    private Long materialId;

    /** 物料编码 */
    @ExcelProperty("物料编码")
    private String materialCode;

    /** 物料名称 */
    @ExcelProperty("物料名称")
    private String materialName;

    /** 客户产品代号 */
    @ExcelProperty("客户产品代号")
    private String customerProductCode;

    /** 客户产品名称 */
    private String customerProductName;

    /** 版本号 */
    @ExcelProperty("版本号")
    private String version;

    /** 发布说明 */
    private String releaseNotes;

    /** 状态: DRAFT/IN_REVIEW/RELEASED/ARCHIVED */
    @ExcelProperty("状态")
    private String status;

    /** 生效日期(空=发布即生效) */
    @ExcelProperty("生效日期")
    private Date effectiveDate;

    /** 过期日期(空=永久有效) */
    @ExcelProperty("过期日期")
    private Date expiryDate;

    /** 是否客户+物料默认发布包(0否/1是) */
    private String isDefault;

    /** 审批流配置ID(wf_custom_form.id) */
    private Long selectFlowId;

    /** 发布人ID */
    private Long publishedBy;

    /** 发布人姓名(由 publishedBy 翻译) */
    @Translation(type = TransConstant.USER_ID_TO_NAME, mapper = "publishedBy")
    private String publishedByName;

    /** 发布时间 */
    private Date publishTime;

    /** 创建者ID */
    private Long createBy;

    /** 创建者姓名(由 createBy 翻译) */
    @Translation(type = TransConstant.USER_ID_TO_NAME, mapper = "createBy")
    private String createByName;

    /** 备注 */
    private String remark;

    /** 创建时间 */
    private Date createTime;

    // ==================== 子表(详情查询时填充) ====================

    /** 固件映射列表 */
    private List<ReleasePkgFirmwareVo> firmwareMappings;

    /** BOM选配列表 */
    private List<ReleasePkgBomItemVo> bomItems;

    /** 配置项列表 */
    private List<ReleasePkgConfigVo> configItems;
}


package com.nexus.core.sch.domain.vo;

import lombok.Data;
import com.nexus.common.core.plus.dto.BasePageDtoRequest;

import java.time.LocalDateTime;

/**
 * 生产工人档案对象 sch_worker vo
 *
 * @author nexus
 * @date 2025-12-19
 */
@Data
public class SchWorkerVo extends BasePageDtoRequest {

    /**
     * 工人档案ID
     */
    private Long workerId;
    /**
     * 关联的系统用户ID
     */
    private Long userId;
    /**
     * 员工工号
     */
    private String employeeId;
    /**
     * 工种
     */
    private String workCategory;
    /**
     * 技能等级
     */
    private String skillLevel;
    /**
     * 在岗状态
     */
    private String workStatus;
    /**
     * 入职日期
     */
    private LocalDateTime entryDate;
    /**
     * 转正日期
     */
    private LocalDateTime regularizationDate;

}
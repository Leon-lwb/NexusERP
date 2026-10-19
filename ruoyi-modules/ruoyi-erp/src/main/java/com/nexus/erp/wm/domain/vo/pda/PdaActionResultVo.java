package com.nexus.erp.wm.domain.vo.pda;

import com.nexus.common.satoken.pda.PdaActionResult;
import lombok.Data;

/**
 * PDA 作业动作统一返回
 * <p>
 * nextAction 取值: SCAN_NEXT_MATERIAL / SCAN_LOCATION / FINISHED, 驱动前端焦点流转
 */
@Data
public class PdaActionResultVo implements PdaActionResult {

    public static final String NEXT_SCAN_MATERIAL = "SCAN_NEXT_MATERIAL";
    public static final String NEXT_SCAN_LOCATION = "SCAN_LOCATION";
    public static final String NEXT_FINISHED = "FINISHED";

    private Boolean ok = Boolean.TRUE;
    private String message;
    private String nextAction;
    /** 库位不符时的推荐库位编码 */
    private String recommendedLocation;
    /** 幂等命中(重复提交)标记 */
    private Boolean replayed;

    /** 原单件过账提交与跨模块确认状态；普通数量模式不赋值。 */
    private String submissionState;
    private String commandId;
    private java.util.List<String> postingGroupIds;
    private java.util.List<String> submissionWarnings;

    public static PdaActionResultVo ok() {
        return new PdaActionResultVo();
    }

    public static PdaActionResultVo ok(String nextAction) {
        PdaActionResultVo vo = new PdaActionResultVo();
        vo.nextAction = nextAction;
        return vo;
    }

    public static PdaActionResultVo fail(String message) {
        PdaActionResultVo vo = new PdaActionResultVo();
        vo.ok = Boolean.FALSE;
        vo.message = message;
        return vo;
    }

    public static PdaActionResultVo replayed() {
        PdaActionResultVo vo = new PdaActionResultVo();
        vo.replayed = Boolean.TRUE;
        vo.message = "重复提交，已处理";
        return vo;
    }
}

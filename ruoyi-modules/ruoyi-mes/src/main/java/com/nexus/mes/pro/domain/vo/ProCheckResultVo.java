package com.nexus.mes.pro.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * MES工序进站/出站校验结果视图对象(P0-3)
 * <p>规则引擎 IProEntryCheckService.runEntryChecks/runExitChecks 返回。</p>
 * <p>passed=true 表示全部规则通过；passed=false 时 failures 含未通过项明细。</p>
 *
 * @author Nexus
 */
@Data
public class ProCheckResultVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 工序记录ID */
    private Long stepRecordId;

    /** 是否通过(全部规则 PASS) */
    private Boolean passed;

    /** 未通过规则明细(passed=false 时填充) */
    private List<CheckFailure> failures = new ArrayList<>();

    /** 是否已推送SOP(进站通过后推送,仅 entry 场景) */
    private Boolean sopPushed;

    /**
     * 校验未通过明细项
     */
    @Data
    public static class CheckFailure implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        /** 校验类型(对应 ProStepCheckRule.checkType) */
        private String checkType;

        /** 失败提示(优先取规则配置的 error_msg,否则取处理器默认消息) */
        private String errorMsg;

        public CheckFailure() {
        }

        public CheckFailure(String checkType, String errorMsg) {
            this.checkType = checkType;
            this.errorMsg = errorMsg;
        }
    }
}

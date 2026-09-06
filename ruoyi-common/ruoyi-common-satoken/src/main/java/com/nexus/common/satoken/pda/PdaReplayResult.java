package com.nexus.common.satoken.pda;

import lombok.Data;

/**
 * 幂等重放响应体(ok=true + replayed=true)
 * <p>与 MES/ERP 各自 ActionResultVo 的 replayed() JSON 形态兼容, 客户端按
 * {@code data.ok}/{@code data.replayed} 判断重放, 不感知具体 VO 类型。</p>
 */
@Data
public class PdaReplayResult implements PdaActionResult {

    private Boolean ok = Boolean.TRUE;

    private String message = "重复提交，已处理";

    /** 幂等重放标记: 重复请求被服务端忽略时返回 */
    private Boolean replayed = Boolean.TRUE;
}

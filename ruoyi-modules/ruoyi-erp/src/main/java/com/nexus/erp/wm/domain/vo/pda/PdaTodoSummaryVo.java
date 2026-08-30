package com.nexus.erp.wm.domain.vo.pda;

import lombok.Data;

import java.util.List;

/**
 * PDA 任务中心待办聚合返回
 * <p>
 * 按当前用户权限过滤各流程的待办数量, 驱动作业台待办卡片区。
 */
@Data
public class PdaTodoSummaryVo {

    private List<TodoItem> items;

    @Data
    public static class TodoItem {
        /** 流程标识: receive/issue/transfer/stocktaking (MES 接入后追加) */
        private String type;
        /** 展示标题: 收货/发料/移库/盘点 */
        private String title;
        /** 可作业单据数 */
        private Long count;
        /** PDA 页面路径 (点击直达) */
        private String path;
    }
}

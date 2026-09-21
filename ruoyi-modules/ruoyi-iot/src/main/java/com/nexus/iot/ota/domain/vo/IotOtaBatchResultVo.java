package com.nexus.iot.ota.domain.vo;

import com.nexus.iot.ota.domain.IotOtaTask;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 按分组批量下发升级任务的结果（部分成功语义）。
 * <p>
 * 批量下发**不整体回滚**：某台设备被禁用/已存在进行中任务时跳过并记入 {@code messages}，
 * 其余设备照常创建，前端据此提示"成功 N 台 / 跳过 M 台"。
 *
 * @author nexus
 */
@Data
public class IotOtaBatchResultVo {

    /**
     * 批次号（写入每条任务的 batch_no，可用于按批次筛选与整体回查）
     */
    private String batchNo;

    /**
     * 目标设备总数
     */
    private Integer total = 0;

    /**
     * 成功创建的任务数
     */
    private Integer created = 0;

    /**
     * 跳过的设备数（含原因，见 messages）
     */
    private Integer skipped = 0;

    /**
     * 已创建的任务
     */
    private List<IotOtaTask> tasks = new ArrayList<>();

    /**
     * 跳过原因（逐条，格式：设备[deviceId]未创建：原因）
     */
    private List<String> messages = new ArrayList<>();
}

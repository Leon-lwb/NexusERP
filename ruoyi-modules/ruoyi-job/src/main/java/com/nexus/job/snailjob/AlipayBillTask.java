package com.nexus.job.snailjob;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.StrUtil;
import com.aizuda.snailjob.client.job.core.annotation.JobExecutor;
import com.aizuda.snailjob.client.job.core.dto.JobArgs;
import com.aizuda.snailjob.common.log.SnailJobLog;
import com.aizuda.snailjob.model.dto.ExecuteResult;
import com.nexus.common.json.utils.JsonUtils;
import com.nexus.job.entity.BillDto;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * DAG工作流任务-模拟支付宝账单任务
 * <a href="https://juejin.cn/post/7487860254114644019"></a>
 *
 * @author 老马
 */
@Component
@JobExecutor(name = "alipayBillTask")
public class AlipayBillTask {

    /**
     * 生成支付宝模拟账单并写入 DAG 工作流上下文。
     * <p>构造一笔固定的支付宝渠道账单（含清算日期与金额），写入工作流上下文，
     * 供后续汇总任务读取。清算日期若为 {@code sysdate} 则取当前日期。</p>
     *
     * @param jobArgs 任务参数与 DAG 工作流上下文
     * @return 执行结果，成功时携带账单数据
     * @throws InterruptedException 线程中断时抛出
     */
    public ExecuteResult jobExecute(JobArgs jobArgs) throws InterruptedException {
        BillDto billDto = new BillDto();
        billDto.setBillId(23456789L);
        billDto.setBillChannel("alipay");
        // 设置清算日期
        String settlementDate = (String) jobArgs.getWfContext().get("settlementDate");
        if (StrUtil.equals(settlementDate, "sysdate")) {
            settlementDate = DateUtil.today();
        }
        billDto.setBillDate(settlementDate);
        billDto.setBillAmount(new BigDecimal("2345.67"));
        // 把billDto对象放入上下文进行传递
        jobArgs.appendContext("alipay", JsonUtils.toJsonString(billDto));
        SnailJobLog.REMOTE.info("上下文: {}", jobArgs.getWfContext());
        return ExecuteResult.success(billDto);
    }

}

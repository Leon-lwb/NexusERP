package com.nexus.job.snailjob;

import cn.hutool.core.util.RandomUtil;
import com.aizuda.snailjob.client.job.core.annotation.JobExecutor;
import com.aizuda.snailjob.client.job.core.dto.JobArgs;
import com.aizuda.snailjob.common.log.SnailJobLog;
import com.aizuda.snailjob.model.dto.ExecuteResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 广播任务
 * <a href="https://juejin.cn/post/7422948006150438950"></a>
 *
 * @author 老马
 */
@Slf4j
@Component
@JobExecutor(name = "testBroadcastJob")
@ConditionalOnProperty(prefix = "snail-job", name = "enabled", havingValue = "true")
public class TestBroadcastJob {

    /** 客户端监听端口，取自配置 {@code snail-job.port}，用于日志中标识执行节点。 */
    @Value("${snail-job.port}")
    private int clientPort;

    /**
     * 广播任务示例：生成随机数，小于 50 时随机失败以触发重试，否则执行成功。
     *
     * @param jobArgs 任务参数
     * @return 执行结果，随机数小于 50 时失败，否则成功
     */
    public ExecuteResult jobExecute(JobArgs jobArgs) {
        int randomInt = RandomUtil.randomInt(100);
        log.info("随机数: {}", randomInt);
        SnailJobLog.REMOTE.info("随机数: {},客户端端口:{}", randomInt, clientPort);
        if (randomInt < 50) {
            throw new RuntimeException("随机数小于50，收集日志任务执行失败");
        }
        // 获得jobArgs 中传入的相加的两个数
        return ExecuteResult.success("随机数大于50，收集日志任务执行成功");
    }

}

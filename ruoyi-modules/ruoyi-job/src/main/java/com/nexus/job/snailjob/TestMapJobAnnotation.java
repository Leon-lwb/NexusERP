package com.nexus.job.snailjob;

import cn.hutool.core.thread.ThreadUtil;
import cn.hutool.extra.spring.SpringUtil;
import com.aizuda.snailjob.client.job.core.MapHandler;
import com.aizuda.snailjob.client.job.core.annotation.JobExecutor;
import com.aizuda.snailjob.client.job.core.annotation.MapExecutor;
import com.aizuda.snailjob.client.job.core.dto.MapArgs;
import com.aizuda.snailjob.common.log.SnailJobLog;
import com.aizuda.snailjob.model.dto.ExecuteResult;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Map任务 动态分配 只分片不关注结果
 * <a href="https://juejin.cn/post/7446362500478894106"></a>
 *
 * @author 老马
 */
@SuppressWarnings({"unchecked", "rawtypes"})
@Component
@JobExecutor(name = "testMapJobAnnotation")
public class TestMapJobAnnotation {

    /**
     * Map 任务入口：将 1~200 的整数按每片 50 个分片后动态派发。
     * <p>生成 1~200 的数值并均分为多片，交由 {@code doCalc} 子任务并行计算。</p>
     *
     * @param mapArgs    Map 任务参数
     * @param mapHandler 分片派发处理器
     * @return 执行结果，派发完成后返回成功
     */
    @MapExecutor
    public ExecuteResult doJobMapExecute(MapArgs mapArgs, MapHandler mapHandler) {
        // 生成1~200数值并分片
        int partitionSize = 50;
        List<List<Integer>> partition = IntStream.rangeClosed(1, 200)
            .boxed()
            .collect(Collectors.groupingBy(i -> (i - 1) / partitionSize))
            .values()
            .stream()
            .toList();
        SnailJobLog.REMOTE.info("端口:{}完成分配任务", SpringUtil.getProperty("server.port"));
        return mapHandler.doMap(partition, "doCalc");
    }

    /**
     * 子任务：对分片内的整数求和并上报结果。
     *
     * @param mapArgs 分片参数，含本片待计算的整数列表
     * @return 执行结果，成功时携带本片累加值
     */
    @MapExecutor(taskName = "doCalc")
    public ExecuteResult doCalc(MapArgs mapArgs) {
        List<Integer> sourceList = (List<Integer>) mapArgs.getMapResult();
        // 遍历sourceList的每一个元素,计算出一个累加值partitionTotal
        int partitionTotal = sourceList.stream().mapToInt(i -> i).sum();
        // 打印日志到服务器
        ThreadUtil.sleep(3, TimeUnit.SECONDS);
        SnailJobLog.REMOTE.info("端口:{},partitionTotal:{}", SpringUtil.getProperty("server.port"), partitionTotal);
        return ExecuteResult.success(partitionTotal);
    }

}

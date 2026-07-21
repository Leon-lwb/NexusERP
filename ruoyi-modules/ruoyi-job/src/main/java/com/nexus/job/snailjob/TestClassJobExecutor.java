package com.nexus.job.snailjob;

import com.aizuda.snailjob.client.job.core.dto.JobArgs;
import com.aizuda.snailjob.client.job.core.executor.AbstractJobExecutor;
import com.aizuda.snailjob.model.dto.ExecuteResult;
import org.springframework.stereotype.Component;

/**
 * 测试类任务执行器
 *
 * @author wanghuan
 * @date 2024-05-17
 */
@Component
public class TestClassJobExecutor extends AbstractJobExecutor {

    /**
     * 类式任务执行器示例，直接返回测试成功结果。
     *
     * @param jobArgs 任务参数
     * @return 执行结果，固定返回「TestJobExecutor测试成功」
     */
    @Override
    protected ExecuteResult doJobExecute(JobArgs jobArgs) {
        return ExecuteResult.success("TestJobExecutor测试成功");
    }
}

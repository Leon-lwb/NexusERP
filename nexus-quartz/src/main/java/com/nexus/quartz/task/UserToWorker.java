package com.nexus.quartz.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.nexus.common.constant.NexusConstants;
import com.nexus.common.constant.UserConstants;
import com.nexus.common.core.domain.entity.SysUser;

import com.nexus.system.service.ISysUserService;
import com.nexus.core.sch.domain.SchWorker;
import com.nexus.core.sch.service.ISchWorkerService;
import com.nexus.system.strategy.AutoCodeUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.List;

/**
 * 定时调度任务：将user表数据中符合要求的账号同步到sch_worker表中
 * @author wanghuan
 * @date 2025/12/19
 */
@Component("userToWorker")
public class UserToWorker {

    private static final Logger log = LoggerFactory.getLogger(UserToWorker.class);

    @Autowired
    private ISysUserService userService;

    @Autowired
    private ISchWorkerService schWorkerService;

    @Autowired
    private AutoCodeUtil autoCodeUtil;

    /**
     * 将user表数据中符合要求的账号同步到sch_worker表中
     * 第一次同步全部数据，后续增量同步
     * 要求同步数据：账号状态为正常，在生产中心部门列表中
     */
    public void syncUserToWorker() {
        log.info("开始同步用户数据到工作表...");

        // 创建查询条件，只查询状态为正常，部门为生产中心账号
        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysUser::getDelFlag, 0L);
        wrapper.eq(SysUser::getStatus, UserConstants.NORMAL);
        wrapper.eq(SysUser::getDeptId, 132L);
        List<SysUser> userList = userService.list(wrapper);

        // 遍历处理每条记录
        int successCount = 0;
        for (SysUser user : userList) {
            try {
                // 检查是否已经同步过
                if (!isUserSynced(user.getUserId())) {
                    // 执行同步操作
                    syncSingleUser(user);
                    successCount++;
                }
            } catch (Exception e) {
                log.error("同步用户失败: " + user.getUserName(), e);
            }
        }

        log.info("同步完成！成功同步 {} 条记录", successCount);
    }

    /**
     * 检查用户是否已经同步到sch_worker表
     * @param userId 用户ID
     * @return 是否已同步
     */
    private boolean isUserSynced(Long userId) {
        LambdaQueryWrapper<SchWorker> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SchWorker::getUserId, userId);
        return schWorkerService.count(wrapper) > 0;
    }

    /**
     * 同步单个用户到sch_worker表
     * @param user 系统用户
     */
    private void syncSingleUser(SysUser user) {
        SchWorker worker = new SchWorker();
        worker.setUserId(user.getUserId());
        worker.setCreateBy("admin");
        worker.setCreateTime(new Date());

        // 自动生成员工工号
        String employeeId = autoCodeUtil.genSerialCode("SCH_WORKER_CODE", null);
        worker.setEmployeeId(employeeId);

        schWorkerService.save(worker);
        log.info("成功同步用户: {}", user.getUserName());
    }
}
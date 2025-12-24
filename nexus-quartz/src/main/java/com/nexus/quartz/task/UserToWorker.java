package com.nexus.quartz.task;

import com.nexus.common.constant.UserConstants;
import com.nexus.common.core.domain.entity.SysUser;
import com.nexus.common.core.domain.entity.vo.SysUserVo;
import com.nexus.common.core.domain.model.LoginUser;

import com.nexus.system.service.ISysUserService;
import com.nexus.core.sch.domain.SchWorker;
import com.nexus.core.sch.domain.dto.SchWorkerDTO;
import com.nexus.core.sch.mapper.SchWorkerMapper;
import com.nexus.core.sch.service.ISchWorkerService;
import com.nexus.system.strategy.AutoCodeUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

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
    private SchWorkerMapper schWorkerMapper; // 添加Mapper注入

    @Autowired
    private AutoCodeUtil autoCodeUtil;

    /**
     * 将user表数据中符合要求的账号同步到sch_worker表中
     * 第一次同步全部数据，后续增量同步
     * 要求同步数据：账号状态为正常，在生产中心部门列表中
     */
    public void syncUserToWorker() {
        log.info("开始同步用户数据到工作表...");
        
        // 临时设置管理员身份以绕过数据权限检查
        setAdminContext();
        
        try {
            // 创建查询条件对象，避免设置可能导致类型处理问题的字段
            SysUser userCondition = new SysUser();
            userCondition.setDelFlag("0");
            userCondition.setStatus(UserConstants.NORMAL);
            //userCondition.setDeptId(132L);
            // 修改后：支持多个部门ID
            List<Long> deptIds = Arrays.asList(111L, 128L, 129L, 130L, 131L, 132L); // 暂时固定值查询 TODO 后期修改为动态查询
            userCondition.getParams().put("deptIds", deptIds);
            
            List<SysUser> userList = userService.selectUserList(userCondition);
            
            // 转换为 SysUserVo 列表
            List<SysUserVo> userVoList = userList.stream()
                .map(this::convertToVo)
                .collect(Collectors.toList());
            
            // 遍历处理每条记录
            int successCount = 0;
            for (SysUserVo userVo : userVoList) {
                try {
                    // 检查是否已经同步过
                    if (!isUserSynced(userVo.getUserId())) {
                        // 执行同步操作
                        syncSingleUser(userVo);
                        successCount++;
                    }
                } catch (Exception e) {
                    log.error("同步用户失败: " + userVo.getUserName(), e);
                }
            }
            
            log.info("同步完成！成功同步 {} 条记录", successCount);
        } finally {
            // 清理安全上下文
            clearSecurityContext();
        }
    }

    /**
     * 检查用户是否已经同步到sch_worker表
     * @param userId 用户ID
     * @return 是否已同步
     */
    private boolean isUserSynced(Long userId) {
        return schWorkerService.lambdaQuery()
            .eq(SchWorker::getUserId, userId)
            .count() > 0;
    }

    /**
     * 同步单个用户到sch_worker表
     * @param userVo 系统用户VO
     */
    private void syncSingleUser(SysUserVo userVo) {
        SchWorkerDTO worker = new SchWorkerDTO(); // 使用DTO类
        worker.setUserId(userVo.getUserId());
        worker.setUserName(userVo.getUserName());
        worker.setPhonenumber(userVo.getPhonenumber());
        worker.setSex(userVo.getSex());
        worker.setEntryDate(userVo.getCreateTime());
        worker.setCreateBy("admin");
        worker.setCreateTime(new Date());
        worker.setRemark("定时任务同步用户");

        // 自动生成员工工号
        String employeeId = autoCodeUtil.genSerialCode("SCH_WORKER_CODE", null);
        worker.setEmployeeId(employeeId);

        // 使用Mapper保存DTO对象，避免params字段处理问题
        schWorkerMapper.insertSchWorkerDTO(worker);
        log.info("成功同步用户: {}", userVo.getUserName());
    }
    
    /**
     * 将SysUser转换为SysUserVo
     * @param user SysUser对象
     * @return SysUserVo对象
     */
    private SysUserVo convertToVo(SysUser user) {
        SysUserVo vo = new SysUserVo();
        vo.setUserId(user.getUserId());
        vo.setDeptId(user.getDeptId());
        vo.setUserName(user.getUserName());
        vo.setNickName(user.getNickName());
        vo.setEmail(user.getEmail());
        vo.setPhonenumber(user.getPhonenumber());
        vo.setSex(user.getSex());
        vo.setAvatar(user.getAvatar());
        vo.setPassword(user.getPassword());
        vo.setStatus(user.getStatus());
        vo.setDelFlag(user.getDelFlag());
        vo.setLoginIp(user.getLoginIp());
        vo.setLoginDate(user.getLoginDate());
        vo.setPwdUpdateDate(user.getPwdUpdateDate());
        vo.setCreateBy(user.getCreateBy());
        vo.setCreateTime(user.getCreateTime());
        vo.setUpdateBy(user.getUpdateBy());
        vo.setUpdateTime(user.getUpdateTime());
        vo.setRemark(user.getRemark());
        return vo;
    }
    
    /**
     * 设置管理员上下文以绕过数据权限检查
     */
    private void setAdminContext() {
        // 创建模拟的管理员用户
        LoginUser loginUser = new LoginUser();
        SysUser adminUser = new SysUser();
        adminUser.setUserId(1L);
        adminUser.setUserName("admin");
        loginUser.setUser(adminUser);
        loginUser.setUserId(1L);
        
        // 创建认证对象
        UsernamePasswordAuthenticationToken authentication = 
            new UsernamePasswordAuthenticationToken(loginUser, null, Collections.emptyList());
        
        // 设置安全上下文
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    /**
     * 清理安全上下文
     */
    private void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }
}
package com.nexus.workflow.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.common.satoken.utils.LoginHelper;
import com.nexus.workflow.domain.WfCopy;
import com.nexus.workflow.domain.bo.WfCopyBo;
import com.nexus.workflow.domain.bo.WfTaskBo;
import com.nexus.workflow.domain.vo.WfCopyVo;
import com.nexus.workflow.mapper.WfCopyMapper;
import com.nexus.workflow.service.IWfCopyService;

import lombok.RequiredArgsConstructor;

import org.flowable.engine.HistoryService;
import org.flowable.engine.history.HistoricProcessInstance;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 流程抄送Service业务层处理
 *
 * @author wanghuan
 * @date 2022-05-19
 */
@RequiredArgsConstructor
@Service
public class WfCopyServiceImpl implements IWfCopyService {

    private final WfCopyMapper baseMapper;

    private final HistoryService historyService;

    /**
     * 查询流程抄送
     *
     * @param copyId 流程抄送主键
     * @return 流程抄送
     */
    @Override
    public WfCopyVo queryById(Long copyId){
        return baseMapper.selectVoById(copyId);
    }

    /**
     * 查询流程抄送列表
     *
     * @param bo 流程抄送
     * @return 流程抄送
     */
    @Override
    public TableDataInfo<WfCopyVo> selectPageList(WfCopyBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<WfCopy> lqw = buildQueryWrapper(bo);
        lqw.orderByDesc(WfCopy::getCreateTime);
        Page<WfCopyVo> result = baseMapper.selectVoPage(pageQuery.build(), lqw);
        return TableDataInfo.build(result);
    }

    /**
     * 查询我的流程抄送列表
     *
     * @param bo 流程抄送
     * @return 流程抄送
     */
    @Override
    public TableDataInfo<WfCopyVo> selectMyPageList(WfCopyBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<WfCopy> lqw = buildMyQueryWrapper(bo);
        lqw.orderByDesc(WfCopy::getCreateTime);
        Page<WfCopyVo> result = baseMapper.selectVoPage(pageQuery.build(), lqw);
        return TableDataInfo.build(result);
    }

    /**
     * 查询流程抄送列表
     *
     * @param bo 流程抄送
     * @return 流程抄送
     */
    @Override
    public List<WfCopyVo> selectList(WfCopyBo bo) {
        LambdaQueryWrapper<WfCopy> lqw = buildQueryWrapper(bo);
        return baseMapper.selectVoList(lqw);
    }

    private LambdaQueryWrapper<WfCopy> buildQueryWrapper(WfCopyBo bo) {
        Map<String, Object> params = bo.getParams();
        LambdaQueryWrapper<WfCopy> lqw = Wrappers.lambdaQuery();
        lqw.eq(bo.getUserId() != null, WfCopy::getUserId, bo.getUserId());
        lqw.like(StringUtils.isNotBlank(bo.getProcessName()), WfCopy::getProcessName, bo.getProcessName());
        lqw.like(StringUtils.isNotBlank(bo.getOriginatorName()), WfCopy::getOriginatorName, bo.getOriginatorName());
        return lqw;
    }

    private LambdaQueryWrapper<WfCopy> buildMyQueryWrapper(WfCopyBo bo) {
        LambdaQueryWrapper<WfCopy> lqw = Wrappers.lambdaQuery();
        // 我的抄送：按服务端登录身份过滤被抄送人，禁止依赖前端传参，
        // 且 Long 之间用 == 比较是引用比较（雪花ID超出缓存区间恒为 false），会导致查询无任何过滤条件
        lqw.eq(WfCopy::getUserId, LoginHelper.getUserId());
        return lqw;
    }

    @Override
    public Boolean makeCopy(WfTaskBo taskBo) {
        if (taskBo == null || StringUtils.isBlank(taskBo.getCopyUserIds())) {
            // 若抄送用户为空，则不需要处理，返回成功
            return true;
        }
        HistoricProcessInstance historicProcessInstance = historyService.createHistoricProcessInstanceQuery()
            .processInstanceId(taskBo.getProcInsId()).singleResult();
        // 实例可能已不存在（误传/已终结清理），无实例信息无法组装抄送标题
        if (historicProcessInstance == null) {
            throw new ServiceException("抄送失败，流程实例不存在：" + taskBo.getProcInsId());
        }
        String[] ids = taskBo.getCopyUserIds().split(",");
        List<WfCopy> copyList = new ArrayList<>(ids.length);
        Long originatorId = LoginHelper.getUserId();
        String originatorName = LoginHelper.getUsername();
        String title = historicProcessInstance.getProcessDefinitionName() + "-" + taskBo.getTaskName();
        for (String id : ids) {
            String trimmed = StringUtils.trim(id);
            if (StringUtils.isEmpty(trimmed)) {
                // 容忍尾逗号/连续逗号造成的空段
                continue;
            }
            Long userId;
            try {
                userId = Long.valueOf(trimmed);
            } catch (NumberFormatException e) {
                // 非数字段直接指出原始值，避免 500 且便于前端修正
                throw new ServiceException("抄送用户ID格式错误：" + trimmed);
            }
            WfCopy copy = new WfCopy();
            copy.setTitle(title);
            copy.setProcessId(historicProcessInstance.getProcessDefinitionId());
            copy.setProcessName(historicProcessInstance.getProcessDefinitionName());
            copy.setDeploymentId(historicProcessInstance.getDeploymentId());
            copy.setInstanceId(taskBo.getProcInsId());
            copy.setTaskId(taskBo.getTaskId());
            copy.setUserId(userId);
            copy.setOriginatorId(originatorId);
            copy.setOriginatorName(originatorName);
            copy.setState("未读");
            copyList.add(copy);
        }
        if (copyList.isEmpty()) {
            return true;
        }
        return baseMapper.insertBatch(copyList);
    }

	@Override
	public void updateStatus(String id) {
		if (StringUtils.isBlank(id)) {
			return;
		}
		WfCopy copy = baseMapper.selectById(id);
		if (copy == null) {
			return;
		}
		// 仅本人（或管理员）可将自己的抄送置为已读，防止任意用户篡改他人抄送状态
		if (!LoginHelper.getUserId().equals(copy.getUserId())
			&& !LoginHelper.isSuperAdmin() && !LoginHelper.isTenantAdmin()) {
			throw new ServiceException("无权操作该抄送记录");
		}
		baseMapper.updateState(id);
	}
}

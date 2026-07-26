package com.nexus.mes.sop.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nexus.common.core.utils.ServletUtils;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.common.satoken.utils.LoginHelper;
import com.nexus.mes.sop.domain.SopViewLog;
import com.nexus.mes.sop.domain.bo.SopViewLogBo;
import com.nexus.mes.sop.domain.vo.SopViewLogVo;
import com.nexus.mes.sop.mapper.SopViewLogMapper;
import com.nexus.mes.sop.service.ISopViewLogService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.Date;
import java.util.List;

/**
 * SOP查阅记录 Service 业务层实现
 * <p>signature_id 预留 (P0-6 ESignatureService 接入后回填, 本周仅记录查阅)</p>
 *
 * @author Nexus
 */
@Slf4j
@RequiredArgsConstructor
@DS("mes")
@Service("sopViewLogService")
public class SopViewLogServiceImpl implements ISopViewLogService {

    private final SopViewLogMapper baseMapper;

    @Override
    public TableDataInfo<SopViewLogVo> selectPageList(SopViewLogBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<SopViewLog> lqw = buildQueryWrapper(bo);
        Page<SopViewLogVo> page = baseMapper.selectVoPage(pageQuery.build(), lqw);
        return TableDataInfo.build(page);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean recordView(Long sopId, String versionNo, Long woId, String woNo,
                              Long processId, String processName, Long workstationId) {
        SopViewLog log = new SopViewLog();
        log.setSopId(sopId);
        log.setVersionNo(versionNo);
        log.setViewerId(LoginHelper.getUserId());
        log.setViewTime(new Date());
        log.setWoId(woId);
        log.setWoNo(woNo);
        log.setProcessId(processId);
        log.setProcessName(processName);
        log.setWorkstationId(workstationId);
        // signature_id = null (预留 P0-6 接入)
        log.setSignatureId(null);
        // IP / User-Agent (防御: 无请求上下文时留空, 如 MQ/定时任务触发)
        try {
            HttpServletRequest req = ServletUtils.getRequest();
            if (req != null) {
                log.setIpAddress(ServletUtils.getClientIP());
                String ua = req.getHeader("User-Agent");
                if (ua != null && ua.length() > 256) {
                    ua = ua.substring(0, 256);
                }
                log.setUserAgent(ua);
            }
        } catch (Exception ignored) {
            // 非请求上下文(如 MQ 触发查阅), IP/UA 留空
        }
        int rows = baseMapper.insert(log);
        return rows > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean deleteByIds(Long[] ids) {
        if (ids == null || ids.length == 0) {
            return false;
        }
        List<Long> idList = Arrays.asList(ids);
        int rows = baseMapper.deleteByIds(idList);
        log.info("批量删除SOP查阅记录: count={}", ids.length);
        return rows > 0;
    }

    private LambdaQueryWrapper<SopViewLog> buildQueryWrapper(SopViewLogBo bo) {
        return Wrappers.<SopViewLog>lambdaQuery()
            .eq(bo.getSopId() != null, SopViewLog::getSopId, bo.getSopId())
            .eq(bo.getViewerId() != null, SopViewLog::getViewerId, bo.getViewerId())
            .eq(bo.getWoId() != null, SopViewLog::getWoId, bo.getWoId())
            .eq(bo.getProcessId() != null, SopViewLog::getProcessId, bo.getProcessId())
            .eq(StringUtils.isNotBlank(bo.getVersionNo()), SopViewLog::getVersionNo, bo.getVersionNo())
            .orderByDesc(SopViewLog::getViewTime);
    }
}

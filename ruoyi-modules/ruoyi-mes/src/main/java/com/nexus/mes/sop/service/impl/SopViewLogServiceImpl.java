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
import com.nexus.common.sign.constants.SignAuthMethod;
import com.nexus.common.sign.constants.SignBizType;
import com.nexus.common.sign.domain.dto.SignCommand;
import com.nexus.common.sign.service.ESignatureService;
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
 * <p>signature_id 由 P0-6 电子签名回填(SOP_VIEW 已阅确认, best-effort 失败留空)</p>
 *
 * @author Nexus
 */
@Slf4j
@RequiredArgsConstructor
@DS("mes")
@Service("sopViewLogService")
public class SopViewLogServiceImpl implements ISopViewLogService {

    private final SopViewLogMapper baseMapper;
    private final ESignatureService eSignatureService;

    /**
     * 分页查询查阅记录列表
     *
     * @param bo        查询参数
     * @param pageQuery 分页参数
     * @return 分页结果
     */
    @Override
    public TableDataInfo<SopViewLogVo> selectPageList(SopViewLogBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<SopViewLog> lqw = buildQueryWrapper(bo);
        Page<SopViewLogVo> page = baseMapper.selectVoPage(pageQuery.build(), lqw);
        return TableDataInfo.build(page);
    }

    /**
     * 登记查阅(查看器打开时调): viewer/time/wo/process/IP/UA, signature_id=P0-6 签名ID
     * <p>IP/UA 从请求上下文获取; 非请求上下文(如 MQ/定时任务)时留空。</p>
     *
     * @param sopId         SOP ID
     * @param versionNo     查阅的版本号
     * @param woId          关联工单ID(可选)
     * @param woNo          工单编号(冗余,可选)
     * @param processId     关联工序ID(可选)
     * @param processName   工序名称(冗余,可选)
     * @param workstationId 工位ID(可选)
     * @return 登记成功返回 true
     */
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
        // P0-6: 回填电子签名ID。查阅是「已阅确认」记录而非授权, 故用 LOGIN_ONLY(不触发二级认证),
        // 且签署失败只告警不阻断(见 signSopView), 查阅登记必须照常落库。
        log.setSignatureId(signSopView(sopId, versionNo));
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

    /**
     * 签署「SOP 已阅确认」事件(best-effort, P0-6)。
     * <p>
     * SOP 查阅是<b>记录</b>而非授权, 故: ① 认证方式用 {@link SignAuthMethod#LOGIN_ONLY}
     * —— 打开查看器不得触发二级认证; ② 不盖章(sealId=null); ③ 签署失败只告警并返回 null,
     * 绝不阻断查阅登记(与 Vision 等辅助服务同一约定, 查阅不会被签名通道故障卡死)。
     * </p>
     *
     * @param sopId     SOP ID
     * @param versionNo 查阅的版本号(可空)
     * @return 签名ID; 签署失败返回 null
     */
    private Long signSopView(Long sopId, String versionNo) {
        try {
            String meaning = StringUtils.isBlank(versionNo)
                ? "SOP 已阅确认" : "SOP 已阅确认（版本 " + versionNo + "）";
            // bizNo 取查阅版本号: 本方法上下文内唯一可得的 SOP 单据标识(无额外查库, 保持非阻塞)
            return eSignatureService.sign(new SignCommand(
                SignBizType.SOP_VIEW, sopId, versionNo, meaning, null, SignAuthMethod.LOGIN_ONLY, null));
        } catch (Exception e) {
            log.warn("SOP查阅签名失败(不阻断查阅登记): sopId={}, versionNo={}", sopId, versionNo, e);
            return null;
        }
    }

    /**
     * 批量删除查阅记录
     *
     * @param ids 查阅记录ID数组
     * @return 删除成功返回 true
     */
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

    /**
     * 构建查询条件: sopId/viewerId/woId/processId/versionNo 精确匹配, 按查阅时间倒序
     */
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

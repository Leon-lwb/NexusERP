package com.nexus.mes.fw.service.impl;

import cn.hutool.crypto.digest.DigestUtil;
import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.dynamic.datasource.toolkit.DynamicDataSourceContextHolder;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nexus.common.core.domain.R;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.common.flowable.core.domain.ActStatus;
import com.nexus.common.flowable.helper.FlowableProcInsIdHelper;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.common.satoken.utils.LoginHelper;
import com.nexus.mes.fw.domain.FwFirmware;
import com.nexus.mes.fw.domain.FwFlashRecord;
import com.nexus.mes.fw.domain.bo.FwFirmwareBo;
import com.nexus.mes.fw.domain.vo.FwFirmwareVo;
import com.nexus.mes.fw.mapper.FwFirmwareMapper;
import com.nexus.mes.fw.mapper.FwFlashRecordMapper;
import com.nexus.mes.fw.service.IFwFirmwareService;
import com.nexus.mes.pro.domain.ProWorkOrderStep;
import com.nexus.mes.pro.mapper.ProWorkOrderStepMapper;
import com.nexus.system.domain.vo.SysOssVo;
import com.nexus.system.service.ISysOssService;
import com.nexus.system.strategy.AutoCodeUtil;
import com.nexus.workflow.domain.WfCustomForm;
import com.nexus.workflow.domain.WfMyBusiness;
import com.nexus.workflow.service.IWfProcessService;
import com.nexus.workflow.service.WfCallBackServiceI;
import com.nexus.workflow.service.impl.WfMyBusinessServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * MES固件档案 Service 业务层实现
 * <p>
 * 状态机: DRAFT -> IN_REVIEW -> RELEASED -> ARCHIVED
 * 审批流: 实现 WfCallBackServiceI (Flowable afterFlowHandle 驱动状态机) + 管理员兜底 approve/reject
 * 编码生成: autoCodeUtil.genSerialCode("MES_FW_CODE", null) -> FW+yyyyMMdd+3位日循环
 * 默认版本: is_default 物料内唯一(仅 RELEASED 可设默认, 归档自动解除)
 * 文件存储: oss_id (MinIO 私有桶存 ID, VO 用 @Translation(OSS_ID_TO_URL) 解析签名URL), MD5/大小随上传回填
 * </p>
 *
 * @author Nexus
 */
@Slf4j
@RequiredArgsConstructor
@DS("mes")
@Service(IFwFirmwareService.SERVICE_NAME)
public class FwFirmwareServiceImpl implements IFwFirmwareService, WfCallBackServiceI {

    private final FwFirmwareMapper baseMapper;
    private final FwFlashRecordMapper flashRecordMapper;
    private final ProWorkOrderStepMapper stepMapper;
    private final AutoCodeUtil autoCodeUtil;
    private final ISysOssService ossService;
    private final IWfProcessService processService;
    private final WfMyBusinessServiceImpl wfMyBusinessServiceImpl;
    private final FlowableProcInsIdHelper procInsIdHelper;

    /**
     * 分页查询固件档案列表
     */
    @Override
    public TableDataInfo<FwFirmwareVo> selectPageList(FwFirmwareBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<FwFirmware> lqw = buildQueryWrapper(bo);
        Page<FwFirmwareVo> page = baseMapper.selectVoPage(pageQuery.build(), lqw);
        return TableDataInfo.build(page);
    }

    /**
     * 根据ID获取固件详情
     */
    @Override
    public FwFirmwareVo selectById(Long firmwareId) {
        return baseMapper.selectVoById(firmwareId);
    }

    /**
     * 新增固件(自动生成编码 + 状态 DRAFT)
     * <p>编码为空时自动生成 MES_FW_CODE; 文件必传(ossId); 同物料同版本唯一。</p>
     *
     * @param bo 固件业务对象(含上传回填的 ossId/fileMd5/fileSize)
     * @return 新增成功返回 true
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean insertByBo(FwFirmwareBo bo) {
        if (StringUtils.isBlank(bo.getFirmwareCode())) {
            bo.setFirmwareCode(generateFirmwareCode());
        }
        Long existCode = baseMapper.selectCount(Wrappers.<FwFirmware>lambdaQuery()
            .eq(FwFirmware::getFirmwareCode, bo.getFirmwareCode()));
        if (existCode != null && existCode > 0) {
            throw new ServiceException("固件编码已存在: " + bo.getFirmwareCode());
        }
        // 同物料同版本唯一(规避重复收录)
        Long existVer = baseMapper.selectCount(Wrappers.<FwFirmware>lambdaQuery()
            .eq(FwFirmware::getMaterialId, bo.getMaterialId())
            .eq(FwFirmware::getVersion, bo.getVersion()));
        if (existVer != null && existVer > 0) {
            throw new ServiceException("该物料已存在版本 " + bo.getVersion() + ", 请检查版本号");
        }
        if (bo.getOssId() == null) {
            throw new ServiceException("固件文件不能为空, 请先上传");
        }
        FwFirmware fw = new FwFirmware();
        fw.setFirmwareCode(bo.getFirmwareCode());
        fw.setFirmwareName(bo.getFirmwareName());
        fw.setMaterialId(bo.getMaterialId());
        fw.setMaterialCode(bo.getMaterialCode());
        fw.setMaterialName(bo.getMaterialName());
        fw.setVersion(bo.getVersion());
        fw.setBuildNo(bo.getBuildNo());
        fw.setOssId(bo.getOssId());
        fw.setFileMd5(bo.getFileMd5());
        fw.setFileSize(bo.getFileSize());
        fw.setReleaseNote(bo.getReleaseNote());
        fw.setRemark(bo.getRemark());
        fw.setStatus(STATUS_DRAFT);
        fw.setIsDefault(IS_DEFAULT_NO);
        boolean ok = baseMapper.insert(fw) > 0;
        log.info("新增固件成功: code={}, materialId={}, version={}", bo.getFirmwareCode(), bo.getMaterialId(), bo.getVersion());
        return ok;
    }

    /**
     * 修改固件(仅 DRAFT/ARCHIVED 可编辑)
     *
     * @param bo 固件业务对象
     * @return 修改成功返回 true
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean updateByBo(FwFirmwareBo bo) {
        FwFirmware fw = baseMapper.selectById(bo.getFirmwareId());
        if (fw == null) {
            throw new ServiceException("固件不存在: " + bo.getFirmwareId());
        }
        if (!STATUS_DRAFT.equals(fw.getStatus()) && !STATUS_ARCHIVED.equals(fw.getStatus())) {
            throw new ServiceException("仅草稿/归档状态可编辑, 当前: " + fw.getStatus());
        }
        // 版本变更时校验同物料同版本唯一(排除自身)
        if (StringUtils.isNotBlank(bo.getVersion()) && !bo.getVersion().equals(fw.getVersion())) {
            Long existVer = baseMapper.selectCount(Wrappers.<FwFirmware>lambdaQuery()
                .eq(FwFirmware::getMaterialId, bo.getMaterialId())
                .eq(FwFirmware::getVersion, bo.getVersion())
                .ne(FwFirmware::getFirmwareId, bo.getFirmwareId()));
            if (existVer != null && existVer > 0) {
                throw new ServiceException("该物料已存在版本 " + bo.getVersion() + ", 请检查版本号");
            }
        }
        fw.setFirmwareName(bo.getFirmwareName());
        fw.setMaterialId(bo.getMaterialId());
        fw.setMaterialCode(bo.getMaterialCode());
        fw.setMaterialName(bo.getMaterialName());
        fw.setVersion(bo.getVersion());
        fw.setBuildNo(bo.getBuildNo());
        fw.setOssId(bo.getOssId());
        fw.setFileMd5(bo.getFileMd5());
        fw.setFileSize(bo.getFileSize());
        fw.setReleaseNote(bo.getReleaseNote());
        fw.setRemark(bo.getRemark());
        boolean ok = baseMapper.updateById(fw) > 0;
        log.info("修改固件成功: firmwareId={}", bo.getFirmwareId());
        return ok;
    }

    /**
     * 批量删除固件(仅 DRAFT 可删)
     * <p>审批中/已发布/已归档固件一律禁删, 保障审批与追溯完整性。</p>
     *
     * @param ids 固件ID数组
     * @return 删除成功返回 true
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean deleteByIds(Long[] ids) {
        if (ids == null || ids.length == 0) {
            return false;
        }
        List<Long> idList = Arrays.asList(ids);
        Long nonDraft = baseMapper.selectCount(Wrappers.<FwFirmware>lambdaQuery()
            .in(FwFirmware::getFirmwareId, idList)
            .ne(FwFirmware::getStatus, STATUS_DRAFT));
        if (nonDraft != null && nonDraft > 0) {
            throw new ServiceException("仅草稿状态固件可删除(审批/发布/归档请先处理)");
        }
        int rows = baseMapper.deleteByIds(idList);
        log.info("批量删除固件: count={}", idList.size());
        return rows > 0;
    }

    /**
     * 上传固件文件: MinIO 存储 + MD5/大小回填
     *
     * @param file 待上传文件
     * @return {ossId, fileName, fileSize, md5}
     */
    @Override
    public Map<String, Object> uploadFirmware(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ServiceException("上传文件不能为空");
        }
        try {
            byte[] bytes = file.getBytes();
            String md5 = DigestUtil.md5Hex(bytes);
            // sys_oss 位于 ry-vue(master) 库, 本服务类级 @DS("mes") 已把线程 DS 切到 mes,
            // 需手动 push("master") 让 OSS 记录写入主库(同 MesMqSendService 先例)
            SysOssVo ossVo;
            DynamicDataSourceContextHolder.push("master");
            try {
                ossVo = ossService.upload(file);
            } finally {
                DynamicDataSourceContextHolder.poll();
            }
            Map<String, Object> result = new HashMap<>();
            result.put("ossId", ossVo.getOssId());
            result.put("fileName", ossVo.getFileName());
            result.put("fileSize", bytes.length);
            result.put("md5", md5);
            log.info("固件文件上传成功: ossId={}, fileName={}, size={}B, md5={}", ossVo.getOssId(), ossVo.getFileName(), bytes.length, md5);
            return result;
        } catch (IOException e) {
            throw new ServiceException("固件文件上传失败: " + e.getMessage());
        }
    }

    /**
     * 提交审批: DRAFT -> IN_REVIEW
     * <p>selectFlowId 非空则走 Flowable: 启动流程(失败抛错回滚, 不静默降级)。<b>启动前不得先改业务行/持行锁</b>——
     * 启动过程中 Flowable(master) 会回调 afterFlowHandle(doing), 其在 REQUIRES_NEW(mes) 里 UPDATE 同一行;
     * 若外层先 UPDATE 持锁, 回调将等待外层提交, 而外层又在等流程启动返回 -> 死锁循环 -> 锁等待超时
     * (同 ERP WmArrivalNotice.submit 先例: 启动前不碰业务行, 状态推进全交给回调)。
     * selectFlowId 在流程启动成功、回调事务已提交后再持久化(此时无锁冲突), 便于流转追溯。
     * selectFlowId 为空则仅改状态(管理员兜底审批)。</p>
     *
     * @param firmwareId   固件ID
     * @param selectFlowId 审批流程配置ID(wf_custom_form.id, 可空, 空则仅改状态)
     * @return 提交成功返回 true
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean submitForApproval(Long firmwareId, String selectFlowId) {
        FwFirmware fw = baseMapper.selectById(firmwareId);
        if (fw == null) {
            throw new ServiceException("固件不存在: " + firmwareId);
        }
        if (!STATUS_DRAFT.equals(fw.getStatus())) {
            throw new ServiceException("仅草稿状态可提交审批, 当前: " + fw.getStatus());
        }
        if (StringUtils.isNotBlank(selectFlowId)) {
            // 走 Flowable: 先启动(回调 afterFlowHandle(doing) 负责置 IN_REVIEW), 成功后再持久化审批流
            Map<String, Object> variables = new HashMap<>();
            variables.put("dataId", firmwareId.toString());
            variables.put("requireTaskOnStart", true);
            variables.put("firmwareId", firmwareId);
            variables.put("firmwareCode", fw.getFirmwareCode());
            variables.put("firmwareName", fw.getFirmwareName());
            variables.put("version", fw.getVersion());
            R<Void> result = processService.startProcessByDataId(firmwareId.toString(), selectFlowId, SERVICE_NAME, variables);
            if (!R.isSuccess(result)) {
                throw new ServiceException("发起审批流程失败: " + result.getMsg());
            }
            // 流程已启动且回调事务已提交(锁已释放), 此刻持久化 select_flow_id 无锁冲突
            baseMapper.update(null, Wrappers.<FwFirmware>lambdaUpdate()
                .set(FwFirmware::getSelectFlowId, Long.valueOf(selectFlowId))
                .eq(FwFirmware::getFirmwareId, firmwareId));
        } else {
            // 管理员兜底: 无流程, CAS DRAFT -> IN_REVIEW
            int rows = baseMapper.update(null, Wrappers.<FwFirmware>lambdaUpdate()
                .set(FwFirmware::getStatus, STATUS_IN_REVIEW)
                .eq(FwFirmware::getFirmwareId, firmwareId)
                .eq(FwFirmware::getStatus, STATUS_DRAFT));
            if (rows == 0) {
                throw new ServiceException("固件状态已变更, 提交审批失败");
            }
        }
        log.info("固件提交审批: firmwareId={}, selectFlowId={}", firmwareId, selectFlowId);
        return true;
    }

    /**
     * Flowable 回调: 驱动固件状态机
     * <p>doing->IN_REVIEW ; pass->RELEASED(记录发布人/时间) ; reject/retrun/stop/recall/revoke->DRAFT</p>
     * <p>必须显式 @DS("mes") + REQUIRES_NEW: 回调由 Flowable(master 上下文)触发,
     * 类级 @DS("mes") 在跨上下文调用中不可靠(同 ProWorkOrderServiceImpl 先例),
     * REQUIRES_NEW 隔离事务避免 Flowable master 事务回滚影响 mes 写入。</p>
     */
    @Override
    @DS("mes")
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void afterFlowHandle(WfMyBusiness business) {
        Long firmwareId = Long.parseLong(business.getDataId());
        FwFirmware fw = baseMapper.selectById(firmwareId);
        if (fw == null) {
            return;
        }
        String actStatus = business.getActStatus();
        if (ActStatus.doing.equals(actStatus)) {
            updateStatus(firmwareId, STATUS_IN_REVIEW);
        } else if (ActStatus.pass.equals(actStatus)) {
            doPublish(firmwareId);
        } else if (ActStatus.reject.equals(actStatus) || ActStatus.retrun.equals(actStatus)
            || ActStatus.stop.equals(actStatus) || ActStatus.recall.equals(actStatus) || ActStatus.revoke.equals(actStatus)) {
            updateStatus(firmwareId, STATUS_DRAFT);
        }
    }

    /**
     * Flowable 回调钩子: 根据业务数据ID获取业务表单数据(固件档案详情)
     * <p>必须显式 @DS("mes") + @Transactional(REQUIRES_NEW): 该回调由 Flowable(master 事务)经 getBean 直接调用。
     * ① 外层 master 事务已绑定连接, 仅靠 @DS 改 holder 无法切换连接(查 fw_firmware 会落到 ry-vue 报表不存在);
     * ② REQUIRES_NEW 挂起外层事务、在 holder top="mes"(方法级 @DS 已 push) 时另起新事务, 新事务绑定 mes 连接;
     *    readOnly 提升只读性能。同 WmArrivalNoticeServiceImpl 先例(ERP 到货通知审批, 已跑通多实例)。</p>
     */
    @Override
    @DS("mes")
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public Object getBusinessDataById(String dataId) {
        try {
            return baseMapper.selectVoById(Long.parseLong(dataId));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Flowable 回调钩子: 返回当前节点的流程变量(固件审批无需额外变量)
     */
    @Override
    public Map<String, Object> flowValuesOfTask(String taskNameId, Map<String, Object> values) {
        return null;
    }

    /**
     * Flowable 回调钩子: 返回当前节点的候选人用户名(固件审批无特殊候选人逻辑)
     */
    @Override
    public List<String> flowCandidateUsernamesOfTask(String taskNameId, Map<String, Object> values) {
        return null;
    }

    /**
     * 兜底审批通过(管理员强制): IN_REVIEW -> RELEASED
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean approve(Long firmwareId, String remark) {
        FwFirmware fw = baseMapper.selectById(firmwareId);
        if (fw == null) {
            throw new ServiceException("固件不存在: " + firmwareId);
        }
        if (!STATUS_IN_REVIEW.equals(fw.getStatus())) {
            throw new ServiceException("仅审批中状态可审批通过, 当前: " + fw.getStatus());
        }
        doPublish(firmwareId);
        log.info("固件兜底审批通过: firmwareId={}, remark={}", firmwareId, remark);
        return true;
    }

    /**
     * 兜底驳回: IN_REVIEW -> DRAFT
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean reject(Long firmwareId, String reason) {
        FwFirmware fw = baseMapper.selectById(firmwareId);
        if (fw == null) {
            throw new ServiceException("固件不存在: " + firmwareId);
        }
        if (!STATUS_IN_REVIEW.equals(fw.getStatus())) {
            throw new ServiceException("仅审批中状态可驳回, 当前: " + fw.getStatus());
        }
        int rows = baseMapper.update(null, Wrappers.<FwFirmware>lambdaUpdate()
            .set(FwFirmware::getStatus, STATUS_DRAFT)
            .eq(FwFirmware::getFirmwareId, firmwareId)
            .eq(FwFirmware::getStatus, STATUS_IN_REVIEW));
        if (rows == 0) {
            throw new ServiceException("固件状态已变更, 驳回失败");
        }
        log.info("固件兜底驳回: firmwareId={}, reason={}", firmwareId, reason);
        return true;
    }

    /**
     * 归档: RELEASED -> ARCHIVED (CAS 状态守卫)
     * <p>构建守卫: 已被工单工序绑定/已产生刷写记录时禁止归档; 归档自动解除默认标记。</p>
     *
     * @param firmwareId 固件ID
     * @param reason     归档原因(可空, 仅记录日志)
     * @return 归档成功返回 true
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean archive(Long firmwareId, String reason) {
        FwFirmware fw = baseMapper.selectById(firmwareId);
        if (fw == null) {
            throw new ServiceException("固件不存在: " + firmwareId);
        }
        if (!STATUS_RELEASED.equals(fw.getStatus())) {
            throw new ServiceException("仅已发布固件可归档, 当前: " + fw.getStatus());
        }
        // 绑定守卫: 已被工单工序绑定(firmware_id)不可归档
        Long boundCount = stepMapper.selectCount(new QueryWrapper<ProWorkOrderStep>()
            .apply("firmware_id = {0}", firmwareId));
        if (boundCount != null && boundCount > 0) {
            throw new ServiceException("固件已被工单工序绑定, 不可归档");
        }
        // 刷写守卫: 已产生刷写记录不可归档(追溯完整性)
        Long flashCount = flashRecordMapper.selectCount(Wrappers.<FwFlashRecord>lambdaQuery()
            .eq(FwFlashRecord::getFirmwareId, firmwareId));
        if (flashCount != null && flashCount > 0) {
            throw new ServiceException("固件已产生刷写记录, 不可归档(追溯完整性)");
        }
        int rows = baseMapper.update(null, Wrappers.<FwFirmware>lambdaUpdate()
            .set(FwFirmware::getStatus, STATUS_ARCHIVED)
            .set(FwFirmware::getIsDefault, IS_DEFAULT_NO)
            .eq(FwFirmware::getFirmwareId, firmwareId)
            .eq(FwFirmware::getStatus, STATUS_RELEASED));
        if (rows == 0) {
            throw new ServiceException("固件状态已变更, 归档失败");
        }
        log.info("固件归档: firmwareId={}, reason={}", firmwareId, reason);
        return true;
    }

    /**
     * 设为物料默认版本(仅 RELEASED, 物料内唯一)
     * <p>同物料其他默认版本自动清 0, CAS 防止并发覆盖。</p>
     *
     * @param firmwareId 固件ID
     * @return 设置成功返回 true
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean setDefault(Long firmwareId) {
        FwFirmware fw = baseMapper.selectById(firmwareId);
        if (fw == null) {
            throw new ServiceException("固件不存在: " + firmwareId);
        }
        if (!STATUS_RELEASED.equals(fw.getStatus())) {
            throw new ServiceException("仅已发布固件可设为默认, 当前: " + fw.getStatus());
        }
        // 同物料其他默认清 0
        baseMapper.update(null, Wrappers.<FwFirmware>lambdaUpdate()
            .set(FwFirmware::getIsDefault, IS_DEFAULT_NO)
            .eq(FwFirmware::getMaterialId, fw.getMaterialId())
            .eq(FwFirmware::getIsDefault, IS_DEFAULT_YES));
        int rows = baseMapper.update(null, Wrappers.<FwFirmware>lambdaUpdate()
            .set(FwFirmware::getIsDefault, IS_DEFAULT_YES)
            .eq(FwFirmware::getFirmwareId, firmwareId)
            .eq(FwFirmware::getStatus, STATUS_RELEASED));
        if (rows == 0) {
            throw new ServiceException("固件状态已变更, 设为默认失败");
        }
        log.info("固件设为默认: firmwareId={}, materialId={}", firmwareId, fw.getMaterialId());
        return true;
    }

    /**
     * 查询可发起审批流程列表(fwFirmwareService 已注册的自定义表单)
     */
    @Override
    public List<WfCustomForm> getFlows() {
        return processService.getProcesssByServiceName(SERVICE_NAME);
    }

    /**
     * 查询流程实例ID(审批表单页"查看流程"跳转用)
     * <p>必须加 @DS("master"): Flowable/wf_my_business 表在 master 库, 类级 @DS("mes") 会导致查询报错
     * (同 ProWorkOrderServiceImpl 先例)。三级降级: wf_my_business -> runtime -> history。</p>
     */
    @Override
    @DS("master")
    public String getProcInsId(String firmwareIdStr) {
        return procInsIdHelper.resolve(firmwareIdStr, () -> {
            WfMyBusiness business = wfMyBusinessServiceImpl.getByDataId(firmwareIdStr);
            return business != null ? business.getProcessInstanceId() : null;
        });
    }

    // ==================== 私有辅助方法 ====================

    /**
     * 内部发布: IN_REVIEW -> RELEASED, 记录发布人/时间
     */
    private void doPublish(Long firmwareId) {
        Date now = new Date();
        Long userId = LoginHelper.getUserId();
        baseMapper.update(null, Wrappers.<FwFirmware>lambdaUpdate()
            .set(FwFirmware::getStatus, STATUS_RELEASED)
            .set(FwFirmware::getPublishedBy, userId)
            .set(FwFirmware::getPublishTime, now)
            .eq(FwFirmware::getFirmwareId, firmwareId)
            .eq(FwFirmware::getStatus, STATUS_IN_REVIEW));
        log.info("固件发布成功: firmwareId={}", firmwareId);
    }

    /**
     * 直接更新固件状态(无 CAS 守卫, 供 Flowable 回调使用)
     */
    private void updateStatus(Long firmwareId, String status) {
        baseMapper.update(null, Wrappers.<FwFirmware>lambdaUpdate()
            .set(FwFirmware::getStatus, status)
            .eq(FwFirmware::getFirmwareId, firmwareId));
    }

    /**
     * 生成固件编码: 走系统自动编码规则 MES_FW_CODE (FW + yyyyMMdd + 3位日循环流水号)
     */
    private String generateFirmwareCode() {
        return autoCodeUtil.genSerialCode("MES_FW_CODE", null);
    }

    /**
     * 构建查询条件: firmwareCode精确/firmwareName模糊/materialId精确/status精确, 按创建时间倒序
     */
    private LambdaQueryWrapper<FwFirmware> buildQueryWrapper(FwFirmwareBo bo) {
        return Wrappers.<FwFirmware>lambdaQuery()
            .eq(StringUtils.isNotBlank(bo.getFirmwareCode()), FwFirmware::getFirmwareCode, bo.getFirmwareCode())
            .like(StringUtils.isNotBlank(bo.getFirmwareName()), FwFirmware::getFirmwareName, bo.getFirmwareName())
            .eq(bo.getMaterialId() != null, FwFirmware::getMaterialId, bo.getMaterialId())
            .eq(StringUtils.isNotBlank(bo.getStatus()), FwFirmware::getStatus, bo.getStatus())
            .orderByDesc(FwFirmware::getCreateTime);
    }
}
package com.nexus.mes.fw.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.core.utils.MapstructUtils;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.common.satoken.utils.LoginHelper;
import com.nexus.mes.eq.domain.EqEquipment;
import com.nexus.mes.eq.mapper.EqEquipmentMapper;
import com.nexus.mes.fw.domain.FwFirmware;
import com.nexus.mes.fw.domain.FwFlashRecord;
import com.nexus.mes.fw.domain.bo.FwFlashRecordBo;
import com.nexus.mes.fw.domain.vo.FwFlashRecordVo;
import com.nexus.mes.fw.mapper.FwFirmwareMapper;
import com.nexus.mes.fw.mapper.FwFlashRecordMapper;
import com.nexus.mes.fw.service.IFwFlashRecordService;
import com.nexus.mes.fw.service.IFwFirmwareService;
import com.nexus.mes.pro.domain.ProSn;
import com.nexus.mes.pro.domain.vo.ProSnVo;
import com.nexus.mes.pro.mapper.ProSnMapper;
import com.nexus.system.strategy.AutoCodeUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.Date;
import java.util.List;

/**
 * MES固件刷写记录 Service 业务层实现
 * <p>
 * M2 刷写作业: 管理员补录(insertByBo)宽松 + 工位作业确认(confirmFlash)严格,
 * 记录号走 MES_FLASH_NO 自动编码(FL+yyyyMMdd+日流水), 冗余字段按主数据回填;
 * 同一 SN 允许多条(FAIL 重刷保留历史, 判定取最新一条)。
 * </p>
 *
 * @author Nexus
 */
@Slf4j
@RequiredArgsConstructor
@DS("mes")
@Service(IFwFlashRecordService.SERVICE_NAME)
public class FwFlashRecordServiceImpl implements IFwFlashRecordService {

    private final FwFlashRecordMapper baseMapper;
    private final FwFirmwareMapper firmwareMapper;
    private final ProSnMapper proSnMapper;
    private final EqEquipmentMapper equipmentMapper;
    private final AutoCodeUtil autoCodeUtil;

    /** SN状态-报废(禁止刷写) */
    private static final String SN_STATUS_SCRAPPED = "SCRAPPED";
    /** 刷写结果-通过/失败 */
    private static final String RESULT_PASS = "PASS";
    private static final String RESULT_FAIL = "FAIL";
    /** 刷写模式-自动/手动 */
    private static final String MODE_AUTO = "AUTO";
    private static final String MODE_MANUAL = "MANUAL";

    /**
     * 分页查询刷写记录
     */
    @Override
    public TableDataInfo<FwFlashRecordVo> selectPageList(FwFlashRecordBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<FwFlashRecord> lqw = Wrappers.lambdaQuery();
        lqw.eq(StringUtils.isNotBlank(bo.getFlashNo()), FwFlashRecord::getFlashNo, bo.getFlashNo())
            .like(StringUtils.isNotBlank(bo.getSnCode()), FwFlashRecord::getSnCode, bo.getSnCode())
            .like(StringUtils.isNotBlank(bo.getWoNo()), FwFlashRecord::getWoNo, bo.getWoNo())
            .like(StringUtils.isNotBlank(bo.getEquipmentCode()), FwFlashRecord::getEquipmentCode, bo.getEquipmentCode())
            .eq(bo.getFirmwareId() != null, FwFlashRecord::getFirmwareId, bo.getFirmwareId())
            .eq(StringUtils.isNotBlank(bo.getResult()), FwFlashRecord::getResult, bo.getResult())
            .eq(StringUtils.isNotBlank(bo.getFlashMode()), FwFlashRecord::getFlashMode, bo.getFlashMode())
            .eq(bo.getOperatorId() != null, FwFlashRecord::getOperatorId, bo.getOperatorId())
            .ge(bo.getBeginFlashTime() != null, FwFlashRecord::getFlashTime, bo.getBeginFlashTime())
            .le(bo.getEndFlashTime() != null, FwFlashRecord::getFlashTime, bo.getEndFlashTime())
            .orderByDesc(FwFlashRecord::getFlashTime);
        Page<FwFlashRecordVo> page = baseMapper.selectVoPage(pageQuery.build(), lqw);
        return TableDataInfo.build(page);
    }

    /**
     * 根据ID查询刷写记录详情
     */
    @Override
    public FwFlashRecordVo selectById(Long flashRecordId) {
        return baseMapper.selectVoById(flashRecordId);
    }

    /**
     * 补录刷写记录(宽松校验, 自动编码 + 冗余回填)
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean insertByBo(FwFlashRecordBo bo) {
        FwFlashRecord record = new FwFlashRecord();
        record.setFlashNo(autoCodeUtil.genSerialCode("MES_FLASH_NO", null));
        record.setSnId(bo.getSnId());
        record.setSnCode(bo.getSnCode());
        record.setMesWoId(bo.getMesWoId());
        record.setWoNo(bo.getWoNo());
        record.setCardId(bo.getCardId());
        record.setStepRecordId(bo.getStepRecordId());
        record.setProcessId(bo.getProcessId());
        record.setProcessCode(bo.getProcessCode());
        record.setProcessName(bo.getProcessName());
        record.setFirmwareId(bo.getFirmwareId());
        record.setFileMd5(bo.getFileMd5());
        record.setEquipmentId(bo.getEquipmentId());
        record.setResult(bo.getResult());
        record.setFailReason(bo.getFailReason());
        record.setRetryCount(bo.getRetryCount() == null ? 0 : bo.getRetryCount());
        record.setFlashMode(StringUtils.isBlank(bo.getFlashMode()) ? MODE_MANUAL : bo.getFlashMode());
        record.setRemark(bo.getRemark());
        record.setFlashTime(bo.getFlashTime() == null ? new Date() : bo.getFlashTime());
        record.setOperatorId(LoginHelper.getUserId());
        record.setOperatorName(LoginHelper.getUsername());
        enrichRedundant(record);
        baseMapper.insert(record);
        log.info("刷写记录补录: flashNo={}, snCode={}, result={}", record.getFlashNo(), record.getSnCode(), record.getResult());
        return true;
    }

    /**
     * 修改刷写记录(纠错, 记录号与SN不可变)
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean updateByBo(FwFlashRecordBo bo) {
        FwFlashRecord record = baseMapper.selectById(bo.getFlashRecordId());
        if (record == null) {
            throw new ServiceException("刷写记录不存在: " + bo.getFlashRecordId());
        }
        record.setFirmwareId(bo.getFirmwareId() == null ? record.getFirmwareId() : bo.getFirmwareId());
        record.setFileMd5(bo.getFileMd5());
        record.setEquipmentId(bo.getEquipmentId());
        record.setResult(bo.getResult());
        record.setFailReason(bo.getFailReason());
        record.setFlashTime(bo.getFlashTime() == null ? record.getFlashTime() : bo.getFlashTime());
        record.setRemark(bo.getRemark());
        // 重刷/纠错场景: 更新对应的固件版本/设备编码/工序冗余快照
        if (bo.getProcessId() != null) {
            record.setProcessId(bo.getProcessId());
            record.setProcessCode(bo.getProcessCode());
            record.setProcessName(bo.getProcessName());
        }
        enrichRedundant(record);
        baseMapper.updateById(record);
        log.info("刷写记录纠错: flashRecordId={}, result={}", bo.getFlashRecordId(), bo.getResult());
        return true;
    }

    /**
     * 批量删除刷写记录(仅 MANUAL 补录记录可删, AUTO 作业记录禁止删除)
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean deleteByIds(Long[] flashRecordIds) {
        List<FwFlashRecord> records = baseMapper.selectBatchIds(Arrays.asList(flashRecordIds));
        for (FwFlashRecord record : records) {
            if (MODE_AUTO.equals(record.getFlashMode())) {
                throw new ServiceException("AUTO 刷写作业记录禁止删除, 保留追溯: " + record.getFlashNo());
            }
        }
        baseMapper.deleteByIds(Arrays.asList(flashRecordIds));
        log.info("刷写记录删除(仅补录): ids={}", Arrays.toString(flashRecordIds));
        return true;
    }

    /**
     * 刷写作业确认: 严格校验 SN 存在且非报废、固件存在且已发布、设备存在
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean confirmFlash(FwFlashRecordBo bo) {
        if (StringUtils.isBlank(bo.getSnCode())) {
            throw new ServiceException("SN编码不能为空");
        }
        if (bo.getFirmwareId() == null) {
            throw new ServiceException("请选择固件");
        }
        if (StringUtils.isBlank(bo.getResult())) {
            throw new ServiceException("请选择刷写结果");
        }
        // SN 校验: 必须存在且非报废
        ProSn sn = proSnMapper.selectOne(Wrappers.<ProSn>lambdaQuery()
            .eq(ProSn::getSnCode, bo.getSnCode().trim()));
        if (sn == null) {
            throw new ServiceException("SN不存在: " + bo.getSnCode());
        }
        if (SN_STATUS_SCRAPPED.equals(sn.getSnStatus())) {
            throw new ServiceException("SN已报废, 禁止刷写: " + bo.getSnCode());
        }
        // 固件校验: 必须存在且已发布
        FwFirmware fw = firmwareMapper.selectById(bo.getFirmwareId());
        if (fw == null) {
            throw new ServiceException("固件不存在: " + bo.getFirmwareId());
        }
        if (!IFwFirmwareService.STATUS_RELEASED.equals(fw.getStatus())) {
            throw new ServiceException("仅已发布(RELEASED)固件可刷写: " + fw.getFirmwareCode() + " 当前=" + fw.getStatus());
        }
        // 设备校验(可选)
        if (bo.getEquipmentId() != null && equipmentMapper.selectById(bo.getEquipmentId()) == null) {
            throw new ServiceException("设备不存在: " + bo.getEquipmentId());
        }
        FwFlashRecord record = new FwFlashRecord();
        record.setFlashNo(autoCodeUtil.genSerialCode("MES_FLASH_NO", null));
        record.setSnId(sn.getSnId());
        record.setSnCode(sn.getSnCode());
        record.setMesWoId(sn.getMesWoId());
        record.setWoNo(sn.getWoNo());
        record.setCardId(sn.getCardId());
        record.setFirmwareId(fw.getFirmwareId());
        record.setFileMd5(bo.getFileMd5());
        record.setEquipmentId(bo.getEquipmentId());
        record.setResult(bo.getResult());
        record.setFailReason(bo.getFailReason());
        record.setRetryCount(bo.getRetryCount() == null ? 0 : bo.getRetryCount());
        record.setFlashMode(StringUtils.isBlank(bo.getFlashMode()) ? MODE_AUTO : bo.getFlashMode());
        record.setProcessId(bo.getProcessId());
        record.setProcessCode(bo.getProcessCode());
        record.setProcessName(bo.getProcessName());
        record.setStepRecordId(bo.getStepRecordId());
        record.setRemark(bo.getRemark());
        record.setFlashTime(bo.getFlashTime() == null ? new Date() : bo.getFlashTime());
        record.setOperatorId(LoginHelper.getUserId());
        record.setOperatorName(LoginHelper.getUsername());
        enrichRedundant(record);
        baseMapper.insert(record);
        log.info("刷写作业确认: flashNo={}, snCode={}, firmwareId={}, result={}", record.getFlashNo(), record.getSnCode(), record.getFirmwareId(), record.getResult());
        return true;
    }

    /**
     * SN 远程搜索: 按 SN 编码模糊查询 pro_sn
     */
    @Override
    public List<ProSnVo> listSnOptions(String snCode) {
        LambdaQueryWrapper<ProSn> lqw = Wrappers.<ProSn>lambdaQuery()
            .like(StringUtils.isNotBlank(snCode), ProSn::getSnCode, StringUtils.isNotBlank(snCode) ? snCode.trim() : null)
            .orderByDesc(ProSn::getCreateTime)
            .last("LIMIT 20");
        return proSnMapper.selectVoList(lqw);
    }

    // ==================== 私有辅助方法 ====================

    /**
     * 冗余字段回填: SN(pro_sn)、固件版本(fw_firmware)、设备编码(eq_equipment)
     */
    private void enrichRedundant(FwFlashRecord record) {
        if (record.getSnId() != null && StringUtils.isBlank(record.getSnCode())) {
            ProSn sn = proSnMapper.selectById(record.getSnId());
            if (sn != null) {
                record.setSnCode(sn.getSnCode());
                if (record.getMesWoId() == null) {
                    record.setMesWoId(sn.getMesWoId());
                    record.setWoNo(sn.getWoNo());
                    record.setCardId(sn.getCardId());
                }
            }
        }
        if (record.getFirmwareId() != null) {
            FwFirmware fw = firmwareMapper.selectById(record.getFirmwareId());
            if (fw != null) {
                record.setFirmwareVersion(fw.getVersion());
                if (StringUtils.isBlank(record.getFileMd5())) {
                    record.setFileMd5(fw.getFileMd5());
                }
            }
        }
        if (record.getEquipmentId() != null && StringUtils.isBlank(record.getEquipmentCode())) {
            EqEquipment eq = equipmentMapper.selectById(record.getEquipmentId());
            if (eq != null) {
                record.setEquipmentCode(eq.getEquipmentCode());
            }
        }
    }
}
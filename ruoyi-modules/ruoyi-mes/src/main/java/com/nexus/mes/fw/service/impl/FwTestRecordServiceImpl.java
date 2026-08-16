package com.nexus.mes.fw.service.impl;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONUtil;
import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.common.satoken.utils.LoginHelper;
import com.nexus.mes.eq.domain.EqEquipment;
import com.nexus.mes.eq.mapper.EqEquipmentMapper;
import com.nexus.mes.fw.domain.FwFirmware;
import com.nexus.mes.fw.domain.FwTestItem;
import com.nexus.mes.fw.domain.FwTestRecord;
import com.nexus.mes.fw.domain.bo.FwTestRecordBo;
import com.nexus.mes.fw.domain.bo.FwTestReportBo;
import com.nexus.mes.fw.domain.vo.FwTestItemVo;
import com.nexus.mes.fw.domain.vo.FwTestRecordVo;
import com.nexus.mes.fw.mapper.FwFirmwareMapper;
import com.nexus.mes.fw.mapper.FwTestRecordMapper;
import com.nexus.mes.fw.service.IFwTestItemService;
import com.nexus.mes.fw.service.IFwTestRecordService;
import com.nexus.mes.pro.domain.ProSn;
import com.nexus.mes.pro.mapper.ProSnMapper;
import com.nexus.system.strategy.AutoCodeUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * MES测试记录 Service 业务层实现 (SN级全检)
 * <p>
 * beginTest 开单: SN 校验(存在/非报废) -> 匹配物料测试项(固件范围过滤) -> MES_TEST_NO 自动编码
 * -> 落待测行(限值/ENUM 快照, result 空); reportTestRows 填报: 按行校验实测值并自动判定
 * (NUMERIC 限值区间 / TEXT 非空 / ENUM 合格值集合), 已判定行保留追溯禁止删除。
 * </p>
 *
 * @author Nexus
 */
@Slf4j
@RequiredArgsConstructor
@DS("mes")
@Service(IFwTestRecordService.SERVICE_NAME)
public class FwTestRecordServiceImpl implements IFwTestRecordService {

    private final FwTestRecordMapper baseMapper;
    private final ProSnMapper proSnMapper;
    private final FwFirmwareMapper firmwareMapper;
    private final EqEquipmentMapper equipmentMapper;
    private final IFwTestItemService testItemService;
    private final AutoCodeUtil autoCodeUtil;

    /** SN状态-报废(禁止开单) */
    private static final String SN_STATUS_SCRAPPED = "SCRAPPED";
    /** 测试模式-自动/手动 */
    private static final String MODE_AUTO = "AUTO";
    private static final String MODE_MANUAL = "MANUAL";

    /**
     * 分页查询测试记录
     */
    @Override
    public TableDataInfo<FwTestRecordVo> selectPageList(FwTestRecordBo bo, PageQuery pageQuery) {
        Page<FwTestRecordVo> page = baseMapper.selectVoPage(pageQuery.build(), Wrappers.<FwTestRecord>lambdaQuery()
            .like(StringUtils.isNotBlank(bo.getTestNo()), FwTestRecord::getTestNo, bo.getTestNo())
            .like(StringUtils.isNotBlank(bo.getSnCode()), FwTestRecord::getSnCode, bo.getSnCode())
            .like(StringUtils.isNotBlank(bo.getWoNo()), FwTestRecord::getWoNo, bo.getWoNo())
            .eq(bo.getFirmwareId() != null, FwTestRecord::getFirmwareId, bo.getFirmwareId())
            .eq(bo.getTestItemId() != null, FwTestRecord::getTestItemId, bo.getTestItemId())
            .eq(StringUtils.isNotBlank(bo.getResult()), FwTestRecord::getResult, bo.getResult())
            .eq(bo.getTesterId() != null, FwTestRecord::getTesterId, bo.getTesterId())
            .eq(StringUtils.isNotBlank(bo.getTestMode()), FwTestRecord::getTestMode, bo.getTestMode())
            .ge(bo.getBeginTestTime() != null, FwTestRecord::getTestTime, bo.getBeginTestTime())
            .le(bo.getEndTestTime() != null, FwTestRecord::getTestTime, bo.getEndTestTime())
            .orderByDesc(FwTestRecord::getTestTime)
            .orderByDesc(FwTestRecord::getTestRecordId));
        return TableDataInfo.build(page);
    }

    /**
     * 根据ID查询测试记录详情
     */
    @Override
    public FwTestRecordVo selectById(Long testRecordId) {
        return baseMapper.selectVoById(testRecordId);
    }

    /**
     * 开单: 匹配测试项落待测行
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<FwTestRecordVo> beginTest(FwTestRecordBo bo) {
        if (StringUtils.isBlank(bo.getSnCode())) {
            throw new ServiceException("SN编码不能为空");
        }
        ProSn sn = proSnMapper.selectOne(Wrappers.<ProSn>lambdaQuery()
            .eq(ProSn::getSnCode, bo.getSnCode().trim()));
        if (sn == null) {
            throw new ServiceException("SN不存在: " + bo.getSnCode());
        }
        if (SN_STATUS_SCRAPPED.equals(sn.getSnStatus())) {
            throw new ServiceException("SN已报废, 禁止测试: " + bo.getSnCode());
        }
        // 固件(可选): 存在则回填版本; 测试允许候选固件(不强制 RELEASED)
        String firmwareVersion = null;
        if (bo.getFirmwareId() != null) {
            FwFirmware fw = firmwareMapper.selectById(bo.getFirmwareId());
            if (fw == null) {
                throw new ServiceException("固件不存在: " + bo.getFirmwareId());
            }
            firmwareVersion = fw.getVersion();
        }
        // 设备(可选)回填编码
        String equipmentCode = null;
        if (bo.getEquipmentId() != null) {
            EqEquipment eq = equipmentMapper.selectById(bo.getEquipmentId());
            if (eq == null) {
                throw new ServiceException("设备不存在: " + bo.getEquipmentId());
            }
            equipmentCode = eq.getEquipmentCode();
        }
        // 匹配测试项标准
        List<FwTestItemVo> items = testItemService.selectByMaterial(sn.getMaterialId(), bo.getFirmwareId());
        if (items == null || items.isEmpty()) {
            throw new ServiceException("该SN物料未配置测试项, 请先在测试项管理中配置: 物料=" + sn.getMaterialCode());
        }
        String testNo = autoCodeUtil.genSerialCode("MES_TEST_NO", null);
        Date now = new Date();
        List<FwTestRecord> records = new ArrayList<>();
        for (FwTestItemVo item : items) {
            FwTestRecord r = new FwTestRecord();
            r.setTestNo(testNo);
            r.setSnId(sn.getSnId());
            r.setSnCode(sn.getSnCode());
            r.setMesWoId(sn.getMesWoId());
            r.setWoNo(sn.getWoNo());
            r.setCardId(sn.getCardId());
            r.setProcessId(bo.getProcessId());
            r.setProcessCode(bo.getProcessCode());
            r.setProcessName(bo.getProcessName());
            r.setFirmwareId(bo.getFirmwareId());
            r.setFirmwareVersion(firmwareVersion);
            r.setTestItemId(item.getTestItemId());
            r.setItemCode(item.getItemCode());
            r.setItemName(item.getItemName());
            r.setDataType(item.getDataType());
            r.setUnit(item.getUnit());
            r.setUpperLimit(item.getUpperLimit());
            r.setLowerLimit(item.getLowerLimit());
            r.setTargetValue(item.getTargetValue());
            r.setPassEnum(item.getPassEnum());
            r.setResult(""); // 待测行默认未测(空串): 数据库列 NOT NULL, 判定后写 PASS/FAIL
            r.setEquipmentId(bo.getEquipmentId());
            r.setEquipmentCode(equipmentCode);
            r.setTestTime(now);
            r.setTestMode(StringUtils.isBlank(bo.getTestMode()) ? MODE_MANUAL : bo.getTestMode());
            r.setTesterId(LoginHelper.getUserId());
            r.setTesterName(LoginHelper.getUsername());
            r.setRemark(bo.getRemark());
            baseMapper.insert(r);
            records.add(r);
        }
        log.info("测试开单: testNo={}, snCode={}, 待测项={}", testNo, sn.getSnCode(), records.size());
        return baseMapper.selectVoList(Wrappers.<FwTestRecord>lambdaQuery()
            .eq(FwTestRecord::getTestNo, testNo)
            .orderByAsc(FwTestRecord::getTestRecordId));
    }

    /**
     * 填报: 批量提交实测值并自动判定
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<FwTestRecordVo> reportTestRows(List<FwTestRecordBo> rows) {
        if (rows == null || rows.isEmpty()) {
            throw new ServiceException("无待填报的测试行");
        }
        Date now = new Date();
        List<FwTestRecordVo> result = new ArrayList<>();
        for (FwTestRecordBo row : rows) {
            if (row.getTestRecordId() == null) {
                throw new ServiceException("测试行ID不能为空");
            }
            FwTestRecord record = baseMapper.selectById(row.getTestRecordId());
            if (record == null) {
                throw new ServiceException("测试行不存在: " + row.getTestRecordId());
            }
            String dataType = record.getDataType();
            boolean byNumeric = IFwTestItemService.DATA_TYPE_NUMERIC.equals(dataType);
            boolean byText = IFwTestItemService.DATA_TYPE_TEXT.equals(dataType);
            boolean byEnum = IFwTestItemService.DATA_TYPE_ENUM.equals(dataType);
            // 校验并判定
            String judged = judge(dataType, row.getNumericValue(), row.getTextValue(),
                record.getLowerLimit(), record.getUpperLimit(), record.getPassEnum());
            if (byNumeric && row.getNumericValue() == null) {
                throw new ServiceException("请填写数值型实测值: " + record.getItemName());
            }
            if ((byText || byEnum) && StringUtils.isBlank(row.getTextValue())) {
                throw new ServiceException("请填写实测值: " + record.getItemName());
            }
            record.setNumericValue(byNumeric ? row.getNumericValue() : null);
            record.setTextValue(byText || byEnum ? row.getTextValue() : null);
            record.setResult(judged);
            record.setTestTime(now);
            record.setRemark(StringUtils.isBlank(row.getRemark()) ? record.getRemark() : row.getRemark());
            baseMapper.updateById(record);
            result.add(baseMapper.selectVoById(record.getTestRecordId()));
        }
        log.info("测试填报: 行数={}", result.size());
        return result;
    }

    /**
     * 批量删除测试记录行(仅未判定草稿行)
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean deleteByIds(Long[] testRecordIds) {
        List<FwTestRecord> records = baseMapper.selectBatchIds(Arrays.asList(testRecordIds));
        for (FwTestRecord record : records) {
            if (StringUtils.isNotBlank(record.getResult())) {
                throw new ServiceException("已判定测试行禁止删除, 保留追溯: " + record.getTestNo() + "/" + record.getItemCode());
            }
        }
        baseMapper.deleteByIds(Arrays.asList(testRecordIds));
        log.info("测试草稿行删除: ids={}", Arrays.toString(testRecordIds));
        return true;
    }

    /**
     * 按测试单号查询全部行
     */
    @Override
    public List<FwTestRecordVo> selectByTestNo(String testNo) {
        return baseMapper.selectVoList(Wrappers.<FwTestRecord>lambdaQuery()
            .eq(FwTestRecord::getTestNo, testNo)
            .orderByAsc(FwTestRecord::getTestRecordId));
    }

    /**
     * 简易统计: 总行数/已判定/通过/失败/通过率
     */
    @Override
    public Map<String, Object> summary(FwTestRecordBo bo) {
        Map<String, Object> map = new HashMap<>();
        long total = baseMapper.selectCount(Wrappers.<FwTestRecord>lambdaQuery()
            .like(StringUtils.isNotBlank(bo.getSnCode()), FwTestRecord::getSnCode, bo.getSnCode())
            .like(StringUtils.isNotBlank(bo.getTestNo()), FwTestRecord::getTestNo, bo.getTestNo()));
        long judged = baseMapper.selectCount(Wrappers.<FwTestRecord>lambdaQuery()
            .like(StringUtils.isNotBlank(bo.getSnCode()), FwTestRecord::getSnCode, bo.getSnCode())
            .isNotNull(FwTestRecord::getResult));
        long pass = baseMapper.selectCount(Wrappers.<FwTestRecord>lambdaQuery()
            .like(StringUtils.isNotBlank(bo.getSnCode()), FwTestRecord::getSnCode, bo.getSnCode())
            .eq(FwTestRecord::getResult, RESULT_PASS));
        long fail = baseMapper.selectCount(Wrappers.<FwTestRecord>lambdaQuery()
            .like(StringUtils.isNotBlank(bo.getSnCode()), FwTestRecord::getSnCode, bo.getSnCode())
            .eq(FwTestRecord::getResult, RESULT_FAIL));
        map.put("total", total);
        map.put("judged", judged);
        map.put("pass", pass);
        map.put("fail", fail);
        map.put("passRate", judged == 0 ? 0 : (int) Math.round(pass * 100.0 / judged));
        return map;
    }

    /**
     * AUTO 设备批量上报测试结果(宽松模式): 逐 SN 开单+判定, 失败项收集不中断
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> reportTestAutoBatch(List<FwTestReportBo> list) {
        if (list == null || list.isEmpty()) {
            throw new ServiceException("上报数据不能为空");
        }
        Map<String, Object> result = new HashMap<>();
        List<Map<String, String>> failed = new ArrayList<>();
        List<Map<String, Object>> details = new ArrayList<>();
        int success = 0;
        for (FwTestReportBo bo : list) {
            try {
                Map<String, Object> single = reportTestAuto(bo);
                success++;
                details.add(single);
            } catch (Exception e) {
                Map<String, String> f = new HashMap<>();
                f.put("snCode", bo.getSnCode());
                f.put("reason", e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
                failed.add(f);
                log.warn("测试AUTO上报失败: snCode={}, reason={}", bo.getSnCode(), f.get("reason"));
            }
        }
        result.put("success", success);
        result.put("failed", failed);
        result.put("details", details);
        return result;
    }

    /**
     * 单 SN AUTO 测试结果上报: 匹配测试项标准自动判定, 落一单多行(test_mode=AUTO)
     */
    private Map<String, Object> reportTestAuto(FwTestReportBo bo) {
        if (StringUtils.isBlank(bo.getSnCode())) {
            throw new ServiceException("SN编码不能为空");
        }
        ProSn sn = proSnMapper.selectOne(Wrappers.<ProSn>lambdaQuery()
            .eq(ProSn::getSnCode, bo.getSnCode().trim()));
        if (sn == null) {
            throw new ServiceException("SN不存在: " + bo.getSnCode());
        }
        if (SN_STATUS_SCRAPPED.equals(sn.getSnStatus())) {
            throw new ServiceException("SN已报废, 禁止测试: " + bo.getSnCode());
        }
        // 固件(可选)回填版本与范围
        String firmwareVersion = null;
        if (bo.getFirmwareId() != null) {
            FwFirmware fw = firmwareMapper.selectById(bo.getFirmwareId());
            if (fw == null) {
                throw new ServiceException("固件不存在: " + bo.getFirmwareId());
            }
            firmwareVersion = fw.getVersion();
        }
        // 设备(可选)按编码匹配
        Long equipmentId = null;
        String equipmentCode = null;
        if (StringUtils.isNotBlank(bo.getEquipmentCode())) {
            EqEquipment eq = equipmentMapper.selectOne(Wrappers.<EqEquipment>lambdaQuery()
                .eq(EqEquipment::getEquipmentCode, bo.getEquipmentCode().trim()));
            if (eq == null) {
                throw new ServiceException("设备不存在: " + bo.getEquipmentCode());
            }
            equipmentId = eq.getEquipmentId();
            equipmentCode = eq.getEquipmentCode();
        }
        // 该 SN 物料测试项标准(固件范围过滤)
        List<FwTestItemVo> standards = testItemService.selectByMaterial(sn.getMaterialId(), bo.getFirmwareId());
        Map<String, FwTestItemVo> standardMap = new HashMap<>();
        if (standards != null) {
            standards.forEach(s -> standardMap.put(s.getItemCode(), s));
        }
        // 同一测试项编码去重: 同一测试单(uk_tenant_test_no_item)内仅一行, 重测保留最后一次上报值
        Map<String, FwTestReportBo.FwTestItemValueBo> dedupItems = new LinkedHashMap<>();
        for (FwTestReportBo.FwTestItemValueBo item : bo.getItems()) {
            dedupItems.put(item.getItemCode(), item);
        }
        // 逐测试项匹配+判定
        String testNo = autoCodeUtil.genSerialCode("MES_TEST_NO", null);
        Date now = new Date();
        List<String> unmatched = new ArrayList<>();
        int judged = 0;
        for (FwTestReportBo.FwTestItemValueBo item : dedupItems.values()) {
            FwTestItemVo standard = standardMap.get(item.getItemCode());
            if (standard == null) {
                unmatched.add(item.getItemCode());
                continue;
            }
            FwTestRecord r = new FwTestRecord();
            r.setTestNo(testNo);
            r.setSnId(sn.getSnId());
            r.setSnCode(sn.getSnCode());
            r.setMesWoId(sn.getMesWoId());
            r.setWoNo(sn.getWoNo());
            r.setCardId(sn.getCardId());
            r.setProcessCode(bo.getProcessCode());
            r.setProcessName(bo.getProcessName());
            r.setFirmwareId(bo.getFirmwareId());
            r.setFirmwareVersion(firmwareVersion);
            r.setTestItemId(standard.getTestItemId());
            r.setItemCode(standard.getItemCode());
            r.setItemName(standard.getItemName());
            r.setDataType(standard.getDataType());
            r.setUnit(standard.getUnit());
            r.setUpperLimit(standard.getUpperLimit());
            r.setLowerLimit(standard.getLowerLimit());
            r.setTargetValue(standard.getTargetValue());
            r.setPassEnum(standard.getPassEnum());
            r.setEquipmentId(equipmentId);
            r.setEquipmentCode(equipmentCode);
            r.setTestTime(now);
            r.setTestMode(MODE_AUTO);
            r.setResult(""); // 先落空, 判定后更新(与 manual 待测行一致)
            baseMapper.insert(r);
            // 判定并更新
            BigDecimal numeric = parseNumeric(item.getValue());
            String text = item.getValue();
            String judgedResult = judge(standard.getDataType(), numeric, text,
                standard.getLowerLimit(), standard.getUpperLimit(), standard.getPassEnum());
            r.setNumericValue(numeric);
            r.setTextValue(text);
            r.setResult(judgedResult);
            baseMapper.updateById(r);
            judged++;
        }
        if (judged == 0) {
            throw new ServiceException("无匹配测试项: " + String.join(",", unmatched));
        }
        Map<String, Object> single = new HashMap<>();
        single.put("snCode", sn.getSnCode());
        single.put("testNo", testNo);
        single.put("judged", judged);
        single.put("unmatched", unmatched);
        log.info("测试AUTO上报: testNo={}, snCode={}, judged={}, unmatched={}", testNo, sn.getSnCode(), judged, unmatched);
        return single;
    }

    /**
     * 解析数值型实测值(非法返回 null, 由判定逻辑处理)
     */
    private BigDecimal parseNumeric(String value) {
        if (StringUtils.isBlank(value)) {
            return null;
        }
        try {
            return new BigDecimal(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // ==================== 私有辅助方法 ====================

    /**
     * 自动判定: NUMERIC 限值区间 / TEXT 非空 / ENUM 合格值集合
     */
    private String judge(String dataType, BigDecimal numericValue, String textValue,
                         BigDecimal lowerLimit, BigDecimal upperLimit, String passEnum) {
        if (IFwTestItemService.DATA_TYPE_NUMERIC.equals(dataType)) {
            if (numericValue == null) {
                return RESULT_FAIL;
            }
            boolean ok = true;
            if (lowerLimit != null && numericValue.compareTo(lowerLimit) < 0) {
                ok = false;
            }
            if (ok && upperLimit != null && numericValue.compareTo(upperLimit) > 0) {
                ok = false;
            }
            return ok ? RESULT_PASS : RESULT_FAIL;
        }
        if (IFwTestItemService.DATA_TYPE_TEXT.equals(dataType)) {
            return StringUtils.isNotBlank(textValue) ? RESULT_PASS : RESULT_FAIL;
        }
        if (IFwTestItemService.DATA_TYPE_ENUM.equals(dataType)) {
            if (StringUtils.isBlank(textValue)) {
                return RESULT_FAIL;
            }
            if (StringUtils.isBlank(passEnum)) {
                return RESULT_PASS; // 未配置合格值集合, 宽松通过
            }
            try {
                JSONArray arr = JSONUtil.parseArray(passEnum);
                for (Object o : arr) {
                    if (textValue.trim().equals(String.valueOf(o).trim())) {
                        return RESULT_PASS;
                    }
                }
            } catch (Exception e) {
                log.warn("ENUM 合格值解析失败, 按宽松通过: passEnum={}", passEnum);
                return RESULT_PASS;
            }
            return RESULT_FAIL;
        }
        return RESULT_FAIL;
    }
}
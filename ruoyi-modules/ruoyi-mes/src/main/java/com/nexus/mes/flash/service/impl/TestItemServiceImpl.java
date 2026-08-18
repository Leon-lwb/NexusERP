package com.nexus.mes.flash.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.mes.flash.domain.TestItem;
import com.nexus.mes.flash.domain.TestRecord;
import com.nexus.mes.flash.domain.bo.TestItemBo;
import com.nexus.mes.flash.domain.vo.TestItemVo;
import com.nexus.mes.flash.mapper.TestItemMapper;
import com.nexus.mes.flash.mapper.TestRecordMapper;
import com.nexus.mes.flash.service.ITestItemService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;

/**
 * MES测试项标准 Service 业务层实现
 * <p>配置校验: 数据类型合法、NUMERIC 上限≥下限、同物料+同固件+同编码唯一;
 * 删除保护: 已被测试记录引用的测试项禁止删除(保留快照追溯)。</p>
 *
 * @author Nexus
 */
@Slf4j
@RequiredArgsConstructor
@DS("mes")
@Service(ITestItemService.SERVICE_NAME)
public class TestItemServiceImpl implements ITestItemService {

    private final TestItemMapper baseMapper;
    private final TestRecordMapper testRecordMapper;

    /** 固件ID: 0 = 该物料所有版本通用 */
    private static final Long FW_ID_ALL = 0L;

    /**
     * 分页查询测试项标准
     */
    @Override
    public TableDataInfo<TestItemVo> selectPageList(TestItemBo bo, PageQuery pageQuery) {
        Page<TestItemVo> page = baseMapper.selectVoPage(pageQuery.build(), Wrappers.<TestItem>lambdaQuery()
            .like(StringUtils.isNotBlank(bo.getItemCode()), TestItem::getItemCode, bo.getItemCode())
            .like(StringUtils.isNotBlank(bo.getItemName()), TestItem::getItemName, bo.getItemName())
            .eq(bo.getMaterialId() != null, TestItem::getMaterialId, bo.getMaterialId())
            .eq(bo.getContentRefId() != null, TestItem::getContentRefId, bo.getContentRefId())
            .eq(StringUtils.isNotBlank(bo.getDataType()), TestItem::getDataType, bo.getDataType())
            .eq(StringUtils.isNotBlank(bo.getIsRequired()), TestItem::getIsRequired, bo.getIsRequired())
            .orderByAsc(TestItem::getSortOrder)
            .orderByAsc(TestItem::getItemCode));
        return TableDataInfo.build(page);
    }

    /**
     * 根据ID查询测试项详情
     */
    @Override
    public TestItemVo selectById(Long testItemId) {
        return baseMapper.selectVoById(testItemId);
    }

    /**
     * 新增测试项
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean insertByBo(TestItemBo bo) {
        validate(bo);
        checkUnique(bo, null);
        TestItem item = new TestItem();
        apply(item, bo);
        if (item.getContentRefId() == null) {
            item.setContentRefId(FW_ID_ALL);
        }
        if (StringUtils.isBlank(item.getIsRequired())) {
            item.setIsRequired("1");
        }
        if (item.getSortOrder() == null) {
            item.setSortOrder(0);
        }
        baseMapper.insert(item);
        log.info("测试项新增: itemCode={}, materialId={}, contentRefId={}", item.getItemCode(), item.getMaterialId(), item.getContentRefId());
        return true;
    }

    /**
     * 修改测试项
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean updateByBo(TestItemBo bo) {
        validate(bo);
        checkUnique(bo, bo.getTestItemId());
        TestItem item = baseMapper.selectById(bo.getTestItemId());
        if (item == null) {
            throw new ServiceException("测试项不存在: " + bo.getTestItemId());
        }
        apply(item, bo);
        baseMapper.updateById(item);
        log.info("测试项修改: testItemId={}, itemCode={}", bo.getTestItemId(), bo.getItemCode());
        return true;
    }

    /**
     * 批量删除测试项(被测试记录引用时禁止)
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean deleteByIds(Long[] testItemIds) {
        for (Long id : testItemIds) {
            Long refCount = testRecordMapper.selectCount(Wrappers.<TestRecord>lambdaQuery()
                .eq(TestRecord::getTestItemId, id));
            if (refCount > 0) {
                TestItem item = baseMapper.selectById(id);
                throw new ServiceException("测试项已被测试记录引用(" + (item != null ? item.getItemCode() : id) + "), 禁止删除");
            }
        }
        baseMapper.deleteByIds(Arrays.asList(testItemIds));
        log.info("测试项删除: ids={}", Arrays.toString(testItemIds));
        return true;
    }

    /**
     * 按物料+固件范围查询(前端下拉/开单快照): 物料匹配 + 固件匹配(0=通用)
     */
    @Override
    public List<TestItemVo> selectByMaterial(Long materialId, Long contentRefId) {
        return baseMapper.selectVoList(Wrappers.<TestItem>lambdaQuery()
            .eq(TestItem::getMaterialId, materialId)
            .and(w -> w.eq(TestItem::getContentRefId, FW_ID_ALL)
                .or(contentRefId != null, q -> q.eq(TestItem::getContentRefId, contentRefId)))
            .orderByAsc(TestItem::getSortOrder)
            .orderByAsc(TestItem::getItemCode));
    }

    // ==================== 私有辅助方法 ====================

    private void validate(TestItemBo bo) {
        if (!DATA_TYPE_NUMERIC.equals(bo.getDataType())
            && !DATA_TYPE_TEXT.equals(bo.getDataType())
            && !DATA_TYPE_ENUM.equals(bo.getDataType())) {
            throw new ServiceException("数据类型不合法: " + bo.getDataType() + " (仅 NUMERIC/TEXT/ENUM)");
        }
        if (DATA_TYPE_NUMERIC.equals(bo.getDataType())) {
            boolean hasLower = bo.getLowerLimit() != null;
            boolean hasUpper = bo.getUpperLimit() != null;
            if (!hasLower && !hasUpper) {
                throw new ServiceException("NUMERIC 类型必须配置下限或上限");
            }
            if (hasLower && hasUpper && bo.getLowerLimit().compareTo(bo.getUpperLimit()) > 0) {
                throw new ServiceException("下限不能大于上限");
            }
            if (bo.getTargetValue() != null) {
                if (hasLower && bo.getTargetValue().compareTo(bo.getLowerLimit()) < 0) {
                    throw new ServiceException("目标值不能小于下限");
                }
                if (hasUpper && bo.getTargetValue().compareTo(bo.getUpperLimit()) > 0) {
                    throw new ServiceException("目标值不能大于上限");
                }
            }
        }
        if (DATA_TYPE_ENUM.equals(bo.getDataType()) && StringUtils.isBlank(bo.getPassEnum())) {
            throw new ServiceException("ENUM 类型必须配置合格值列表");
        }
    }

    private void checkUnique(TestItemBo bo, Long excludeId) {
        Long fwId = bo.getContentRefId() == null ? FW_ID_ALL : bo.getContentRefId();
        Long count = baseMapper.selectCount(Wrappers.<TestItem>lambdaQuery()
            .eq(TestItem::getMaterialId, bo.getMaterialId())
            .eq(TestItem::getContentRefId, fwId)
            .eq(TestItem::getItemCode, bo.getItemCode())
            .ne(excludeId != null, TestItem::getTestItemId, excludeId));
        if (count > 0) {
            throw new ServiceException("该物料下已存在测试项编码: " + bo.getItemCode() + (FW_ID_ALL.equals(fwId) ? " (通用)" : " (固件级)"));
        }
    }

    private void apply(TestItem item, TestItemBo bo) {
        item.setItemCode(bo.getItemCode().trim());
        item.setItemName(bo.getItemName().trim());
        item.setMaterialId(bo.getMaterialId());
        item.setContentRefId(bo.getContentRefId());
        item.setDataType(bo.getDataType());
        item.setUnit(bo.getUnit());
        item.setUpperLimit(bo.getUpperLimit());
        item.setLowerLimit(bo.getLowerLimit());
        item.setTargetValue(bo.getTargetValue());
        item.setPassEnum(bo.getPassEnum());
        item.setIsRequired(bo.getIsRequired());
        item.setSortOrder(bo.getSortOrder());
        item.setRemark(bo.getRemark());
    }
}
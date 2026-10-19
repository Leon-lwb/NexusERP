package com.nexus.mes.lt.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.mes.lt.domain.LtLot;
import com.nexus.mes.lt.domain.LtSnBinding;
import com.nexus.mes.lt.domain.bo.LtSnBindingBo;
import com.nexus.mes.lt.domain.vo.LtSnBindingVo;
import com.nexus.mes.lt.mapper.LtLotMapper;
import com.nexus.mes.lt.mapper.LtSnBindingMapper;
import com.nexus.mes.lt.service.ILtSnBindingService;
import com.nexus.mes.pro.domain.ProSn;
import com.nexus.mes.pro.mapper.ProSnMapper;
import com.nexus.common.satoken.utils.LoginHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * MES SN批次绑定 Service 业务层实现
 * <p>核心校验: 同SN同物料仅允许一个绑定(uk_sn_material 唯一索引兜底)</p>
 *
 * @author Nexus
 */
@Slf4j
@RequiredArgsConstructor
@DS("mes")
@Service("LtSnBindingService")
public class LtSnBindingServiceImpl implements ILtSnBindingService {

    private final LtSnBindingMapper baseMapper;
    private final LtLotMapper lotMapper;
    private final ProSnMapper snMapper;
    private final com.nexus.mes.trace.MesSnCreationWriter productLots;

    /**
     * 分页查询SN批次绑定列表
     * <p>结果中富集物料编码/名称(从 lt_lot 冗余字段), boundByName 由 @Translation 自动翻译。</p>
     *
     * @param bo        查询条件
     * @param pageQuery 分页参数
     * @return SN批次绑定分页列表
     */
    @Override
    public TableDataInfo<LtSnBindingVo> selectPageList(LtSnBindingBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<LtSnBinding> lqw = buildQueryWrapper(bo);
        Page<LtSnBindingVo> page = baseMapper.selectVoPage(pageQuery.build(), lqw);
        // 富集物料编码/名称(从同库 lt_lot 冗余字段); boundByName 由 VO @Translation 自动翻译
        enrichMaterial(page.getRecords());
        return TableDataInfo.build(page);
    }

    /**
     * 根据ID获取绑定详情
     *
     * @param bindingId 绑定ID
     * @return 绑定详情
     */
    @Override
    public LtSnBindingVo selectById(Long bindingId) {
        return baseMapper.selectVoById(bindingId);
    }

    /**
     * 新增SN-批次绑定
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean insertByBo(LtSnBindingBo bo) {
        // 1. 校验 SN 存在
        ProSn sn = snMapper.selectById(bo.getSnId());
        if (sn == null) {
            throw new RuntimeException("SN不存在: " + bo.getSnId());
        }
        // 2. 校验批次存在
        LtLot lot = lotMapper.selectById(bo.getLotId());
        if (lot == null) {
            throw new RuntimeException("批次不存在: " + bo.getLotId());
        }
        if (!Objects.equals(sn.getMaterialId(), bo.getMaterialId())
                || !Objects.equals(lot.getMaterialId(), sn.getMaterialId())) {
            throw new RuntimeException("组件批次必须通过有实际数量和工序的组件消耗账分配，不能用成品批次绑定代替");
        }
        if (sn.getProductLotId() != null && !sn.getProductLotId().equals(bo.getLotId())) {
            throw new RuntimeException("成品批次已指定，变更需保留原凭据并走纠正流程");
        }
        productLots.attachProductLot(bo.getSnId(), bo.getLotId());
        // 3. 校验唯一性: 同SN同物料仅一个绑定(uk_sn_material 兜底,这里友好提示)
        Long existCount =
                baseMapper.selectCount(
                        Wrappers.<LtSnBinding>lambdaQuery()
                                .eq(LtSnBinding::getSnId, bo.getSnId())
                                .eq(LtSnBinding::getMaterialId, bo.getMaterialId()));
        if (existCount != null && existCount > 0) {
            throw new RuntimeException(
                    "SN " + sn.getSnCode() + " 已绑定物料 " + bo.getMaterialId() + " 的批次");
        }
        // 4. 写入(冗余字段)
        LtSnBinding binding = new LtSnBinding();
        binding.setSnId(bo.getSnId());
        binding.setSnCode(sn.getSnCode());
        binding.setLotId(bo.getLotId());
        binding.setLotCode(lot.getLotCode());
        binding.setMaterialId(bo.getMaterialId());
        binding.setBoundTime(new Date());
        binding.setBoundBy(LoginHelper.getUserId());
        int rows = baseMapper.insert(binding);
        log.info(
                "SN-批次绑定: snId={}, lotId={}, materialId={}",
                bo.getSnId(),
                bo.getLotId(),
                bo.getMaterialId());
        return rows > 0;
    }

    /**
     * 批量删除绑定
     *
     * @param ids 绑定ID数组
     * @return 删除成功返回 true
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean deleteByIds(Long[] ids) {
        if (ids == null || ids.length == 0) {
            return false;
        }
        throw new RuntimeException("批次绑定凭据不可删除，组件请使用引用原分录的冲正");
    }

    /**
     * 按SN ID查询其批次绑定(追溯用)
     *
     * @param snId SN ID
     * @return 绑定列表(按绑定时间降序)
     */
    @Override
    public List<LtSnBindingVo> selectBySnId(Long snId) {
        return baseMapper.selectVoList(
                Wrappers.<LtSnBinding>lambdaQuery()
                        .eq(LtSnBinding::getSnId, snId)
                        .orderByDesc(LtSnBinding::getBoundTime));
    }

    /**
     * 按批次ID查询所有SN绑定(追溯用)
     *
     * @param lotId 批次ID
     * @return 绑定列表(按绑定时间降序)
     */
    @Override
    public List<LtSnBindingVo> selectByLotId(Long lotId) {
        return baseMapper.selectVoList(
                Wrappers.<LtSnBinding>lambdaQuery()
                        .eq(LtSnBinding::getLotId, lotId)
                        .orderByDesc(LtSnBinding::getBoundTime));
    }

    // ==================== 私有辅助方法 ====================

    /**
     * 富集物料编码/名称: 批量查 lt_lot(同库冗余字段 materialCode/materialName), 避免跨库调 ERP。
     * boundByName 由 VO 上的 @Translation(USER_ID_TO_NAME) 自动翻译, 无需此处处理。
     */
    private void enrichMaterial(List<LtSnBindingVo> records) {
        if (records == null || records.isEmpty()) {
            return;
        }
        List<Long> lotIds =
                records.stream()
                        .map(LtSnBindingVo::getLotId)
                        .filter(Objects::nonNull)
                        .distinct()
                        .toList();
        if (lotIds.isEmpty()) {
            return;
        }
        Map<Long, LtLot> lotMap =
                lotMapper
                        .selectList(Wrappers.<LtLot>lambdaQuery().in(LtLot::getLotId, lotIds))
                        .stream()
                        .collect(Collectors.toMap(LtLot::getLotId, l -> l, (a, b) -> a));
        for (LtSnBindingVo vo : records) {
            LtLot lot = lotMap.get(vo.getLotId());
            if (lot != null) {
                vo.setMaterialCode(lot.getMaterialCode());
                vo.setMaterialName(lot.getMaterialName());
            }
        }
    }

    /**
     * 构建SN批次绑定查询条件包装器(按 snId/lotId/materialId 筛选)。
     */
    private LambdaQueryWrapper<LtSnBinding> buildQueryWrapper(LtSnBindingBo bo) {
        return Wrappers.<LtSnBinding>lambdaQuery()
                .eq(bo.getSnId() != null, LtSnBinding::getSnId, bo.getSnId())
                .eq(bo.getLotId() != null, LtSnBinding::getLotId, bo.getLotId())
                .eq(bo.getMaterialId() != null, LtSnBinding::getMaterialId, bo.getMaterialId())
                .orderByDesc(LtSnBinding::getBoundTime);
    }
}

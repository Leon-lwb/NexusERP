package org.dromara.erp.wm.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.utils.MapstructUtils;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.erp.wm.domain.WmMiscRecpt;
import org.dromara.erp.wm.domain.WmMiscRecptLine;
import org.dromara.erp.wm.domain.bo.WmMiscRecptBo;
import org.dromara.erp.wm.domain.vo.WmMiscRecptVo;
import org.dromara.erp.wm.mapper.WmMiscRecptLineMapper;
import org.dromara.erp.wm.mapper.WmMiscRecptMapper;
import org.dromara.erp.wm.service.IWmMiscRecptService;
import org.dromara.system.strategy.AutoCodeUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;

@Slf4j
@RequiredArgsConstructor
@DS("erp")
@Service
public class WmMiscRecptServiceImpl implements IWmMiscRecptService {

    private final WmMiscRecptMapper baseMapper;
    private final WmMiscRecptLineMapper lineMapper;
    private final WmStockOperationServiceImpl stockOperationService;
    private final AutoCodeUtil autoCodeUtil;

    @Override
    public TableDataInfo<WmMiscRecptVo> selectPageList(WmMiscRecptBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<WmMiscRecpt> lqw = buildQueryWrapper(bo);
        Page<WmMiscRecptVo> page = baseMapper.selectVoPage(pageQuery.build(), lqw);
        return TableDataInfo.build(page);
    }

    @Override
    public List<WmMiscRecptVo> selectList(WmMiscRecptBo bo) {
        return baseMapper.selectVoList(buildQueryWrapper(bo));
    }

    @Override
    public WmMiscRecptVo selectById(Long id) {
        WmMiscRecptVo vo = baseMapper.selectVoById(id);
        if (vo != null && vo.getRecptId() != null) {
            LambdaQueryWrapper<WmMiscRecptLine> lineLqw = Wrappers.lambdaQuery();
            lineLqw.eq(WmMiscRecptLine::getRecptId, vo.getRecptId());
            vo.setWmMiscRecptLineList(lineMapper.selectList(lineLqw));
        }
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insertByBo(WmMiscRecptBo bo) {
        WmMiscRecpt entity = MapstructUtils.convert(bo, WmMiscRecpt.class);
        entity.setRecptCode(autoCodeUtil.genSerialCode("WM_MISC_RECPT_CODE", null));
        int rows = baseMapper.insert(entity);
        insertOrUpdateLines(entity.getRecptId(), bo.getWmMiscRecptLineList());
        return rows;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateByBo(WmMiscRecptBo bo) {
        WmMiscRecpt entity = MapstructUtils.convert(bo, WmMiscRecpt.class);
        deleteLinesByRecptId(entity.getRecptId());
        insertOrUpdateLines(entity.getRecptId(), bo.getWmMiscRecptLineList());
        return baseMapper.updateById(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteByIds(Long[] ids) {
        for (Long id : ids) {
            deleteLinesByRecptId(id);
        }
        return baseMapper.deleteBatchIds(List.of(ids));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void execute(Long recptId) {
        WmMiscRecpt recpt = baseMapper.selectById(recptId);
        if (recpt == null) {
            throw new RuntimeException("杂项入库单不存在");
        }
        if ("EXECUTED".equals(recpt.getStatus())) {
            throw new RuntimeException("杂项入库单已执行");
        }

        List<WmMiscRecptLine> lines = lineMapper.selectList(
            Wrappers.lambdaQuery(WmMiscRecptLine.class)
                .eq(WmMiscRecptLine::getRecptId, recptId));

        if (lines == null || lines.isEmpty()) {
            throw new RuntimeException("杂项入库单无明细行");
        }

        for (WmMiscRecptLine line : lines) {
            stockOperationService.postMiscRecpt(line, recpt, new Date());
        }

        recpt.setStatus("EXECUTED");
        baseMapper.updateById(recpt);
        log.info("杂项入库单执行完成, recptId={}", recptId);
    }

    private void insertOrUpdateLines(Long recptId, List<WmMiscRecptLine> lineList) {
        if (lineList != null && !lineList.isEmpty()) {
            for (WmMiscRecptLine line : lineList) {
                line.setLineId(null);
                line.setRecptId(recptId);
                lineMapper.insert(line);
            }
        }
    }

    private void deleteLinesByRecptId(Long recptId) {
        if (recptId != null) {
            LambdaQueryWrapper<WmMiscRecptLine> lqw = Wrappers.lambdaQuery();
            lqw.eq(WmMiscRecptLine::getRecptId, recptId);
            lineMapper.delete(lqw);
        }
    }

    private LambdaQueryWrapper<WmMiscRecpt> buildQueryWrapper(WmMiscRecptBo bo) {
        LambdaQueryWrapper<WmMiscRecpt> lqw = Wrappers.lambdaQuery();
        lqw.eq(StringUtils.isNotBlank(bo.getRecptCode()), WmMiscRecpt::getRecptCode, bo.getRecptCode());
        lqw.eq(StringUtils.isNotBlank(bo.getRecptType()), WmMiscRecpt::getRecptType, bo.getRecptType());
        lqw.eq(bo.getWarehouseId() != null, WmMiscRecpt::getWarehouseId, bo.getWarehouseId());
        lqw.eq(StringUtils.isNotBlank(bo.getStatus()), WmMiscRecpt::getStatus, bo.getStatus());
        lqw.orderByDesc(WmMiscRecpt::getCreateTime);
        return lqw;
    }
}

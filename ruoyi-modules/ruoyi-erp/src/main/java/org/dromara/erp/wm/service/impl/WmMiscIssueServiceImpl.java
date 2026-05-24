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
import org.dromara.erp.wm.domain.WmMiscIssue;
import org.dromara.erp.wm.domain.WmMiscIssueLine;
import org.dromara.erp.wm.domain.bo.WmMiscIssueBo;
import org.dromara.erp.wm.domain.vo.WmMiscIssueVo;
import org.dromara.erp.wm.mapper.WmMiscIssueLineMapper;
import org.dromara.erp.wm.mapper.WmMiscIssueMapper;
import org.dromara.erp.wm.service.IWmMiscIssueService;
import org.dromara.system.strategy.AutoCodeUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;

@Slf4j
@RequiredArgsConstructor
@DS("erp")
@Service
public class WmMiscIssueServiceImpl implements IWmMiscIssueService {

    private final WmMiscIssueMapper baseMapper;
    private final WmMiscIssueLineMapper lineMapper;
    private final WmStockOperationServiceImpl stockOperationService;
    private final AutoCodeUtil autoCodeUtil;

    @Override
    public TableDataInfo<WmMiscIssueVo> selectPageList(WmMiscIssueBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<WmMiscIssue> lqw = buildQueryWrapper(bo);
        Page<WmMiscIssueVo> page = baseMapper.selectVoPage(pageQuery.build(), lqw);
        return TableDataInfo.build(page);
    }

    @Override
    public List<WmMiscIssueVo> selectList(WmMiscIssueBo bo) {
        return baseMapper.selectVoList(buildQueryWrapper(bo));
    }

    @Override
    public WmMiscIssueVo selectById(Long id) {
        WmMiscIssueVo vo = baseMapper.selectVoById(id);
        if (vo != null && vo.getIssueId() != null) {
            LambdaQueryWrapper<WmMiscIssueLine> lineLqw = Wrappers.lambdaQuery();
            lineLqw.eq(WmMiscIssueLine::getIssueId, vo.getIssueId());
            vo.setWmMiscIssueLineList(lineMapper.selectList(lineLqw));
        }
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insertByBo(WmMiscIssueBo bo) {
        WmMiscIssue entity = MapstructUtils.convert(bo, WmMiscIssue.class);
        entity.setIssueCode(autoCodeUtil.genSerialCode("WM_MISC_ISSUE_CODE", null));
        int rows = baseMapper.insert(entity);
        insertOrUpdateLines(entity.getIssueId(), bo.getWmMiscIssueLineList());
        return rows;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateByBo(WmMiscIssueBo bo) {
        WmMiscIssue entity = MapstructUtils.convert(bo, WmMiscIssue.class);
        deleteLinesByIssueId(entity.getIssueId());
        insertOrUpdateLines(entity.getIssueId(), bo.getWmMiscIssueLineList());
        return baseMapper.updateById(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteByIds(Long[] ids) {
        for (Long id : ids) {
            deleteLinesByIssueId(id);
        }
        return baseMapper.deleteBatchIds(List.of(ids));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void execute(Long issueId) {
        WmMiscIssue issue = baseMapper.selectById(issueId);
        if (issue == null) {
            throw new RuntimeException("杂项出库单不存在");
        }
        if ("EXECUTED".equals(issue.getStatus())) {
            throw new RuntimeException("杂项出库单已执行");
        }

        List<WmMiscIssueLine> lines = lineMapper.selectList(
            Wrappers.lambdaQuery(WmMiscIssueLine.class)
                .eq(WmMiscIssueLine::getIssueId, issueId));

        if (lines == null || lines.isEmpty()) {
            throw new RuntimeException("杂项出库单无明细行");
        }

        for (WmMiscIssueLine line : lines) {
            stockOperationService.postMiscIssue(line, new Date());
        }

        issue.setStatus("EXECUTED");
        baseMapper.updateById(issue);
        log.info("杂项出库单执行完成, issueId={}", issueId);
    }

    private void insertOrUpdateLines(Long issueId, List<WmMiscIssueLine> lineList) {
        if (lineList != null && !lineList.isEmpty()) {
            for (WmMiscIssueLine line : lineList) {
                line.setLineId(null);
                line.setIssueId(issueId);
                lineMapper.insert(line);
            }
        }
    }

    private void deleteLinesByIssueId(Long issueId) {
        if (issueId != null) {
            LambdaQueryWrapper<WmMiscIssueLine> lqw = Wrappers.lambdaQuery();
            lqw.eq(WmMiscIssueLine::getIssueId, issueId);
            lineMapper.delete(lqw);
        }
    }

    private LambdaQueryWrapper<WmMiscIssue> buildQueryWrapper(WmMiscIssueBo bo) {
        LambdaQueryWrapper<WmMiscIssue> lqw = Wrappers.lambdaQuery();
        lqw.eq(StringUtils.isNotBlank(bo.getIssueCode()), WmMiscIssue::getIssueCode, bo.getIssueCode());
        lqw.eq(StringUtils.isNotBlank(bo.getIssueType()), WmMiscIssue::getIssueType, bo.getIssueType());
        lqw.eq(bo.getWarehouseId() != null, WmMiscIssue::getWarehouseId, bo.getWarehouseId());
        lqw.eq(StringUtils.isNotBlank(bo.getStatus()), WmMiscIssue::getStatus, bo.getStatus());
        lqw.orderByDesc(WmMiscIssue::getCreateTime);
        return lqw;
    }
}

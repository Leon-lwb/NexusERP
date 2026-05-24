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
import org.dromara.erp.wm.domain.WmProductionIssue;
import org.dromara.erp.wm.domain.WmProductionIssueLine;
import org.dromara.erp.wm.domain.bo.WmProductionIssueBo;
import org.dromara.erp.wm.domain.vo.WmProductionIssueVo;
import org.dromara.erp.wm.mapper.WmProductionIssueLineMapper;
import org.dromara.erp.wm.mapper.WmProductionIssueMapper;
import org.dromara.erp.wm.service.IWmProductionIssueService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;

@Slf4j
@RequiredArgsConstructor
@DS("erp")
@Service
public class WmProductionIssueServiceImpl implements IWmProductionIssueService {

    private final WmProductionIssueMapper baseMapper;
    private final WmProductionIssueLineMapper lineMapper;
    private final WmStockOperationServiceImpl stockOperationService;

    @Override
    public TableDataInfo<WmProductionIssueVo> selectPageList(WmProductionIssueBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<WmProductionIssue> lqw = buildQueryWrapper(bo);
        Page<WmProductionIssueVo> page = baseMapper.selectVoPage(pageQuery.build(), lqw);
        return TableDataInfo.build(page);
    }

    @Override
    public List<WmProductionIssueVo> selectList(WmProductionIssueBo bo) {
        return baseMapper.selectVoList(buildQueryWrapper(bo));
    }

    @Override
    public WmProductionIssueVo selectById(Long id) {
        WmProductionIssueVo vo = baseMapper.selectVoById(id);
        if (vo != null && vo.getIssueId() != null) {
            LambdaQueryWrapper<WmProductionIssueLine> lineLqw = Wrappers.lambdaQuery();
            lineLqw.eq(WmProductionIssueLine::getIssueId, vo.getIssueId());
            vo.setWmProductionIssueLineList(lineMapper.selectList(lineLqw));
        }
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insertByBo(WmProductionIssueBo bo) {
        WmProductionIssue entity = MapstructUtils.convert(bo, WmProductionIssue.class);
        int rows = baseMapper.insert(entity);
        insertOrUpdateLines(entity.getIssueId(), bo.getWmProductionIssueLineList());
        return rows;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateByBo(WmProductionIssueBo bo) {
        WmProductionIssue entity = MapstructUtils.convert(bo, WmProductionIssue.class);
        deleteLinesByIssueId(entity.getIssueId());
        insertOrUpdateLines(entity.getIssueId(), bo.getWmProductionIssueLineList());
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
        WmProductionIssue issue = baseMapper.selectById(issueId);
        if (issue == null) {
            throw new RuntimeException("生产领料单不存在");
        }
        if ("EXECUTED".equals(issue.getStatus())) {
            throw new RuntimeException("生产领料单已执行");
        }

        List<WmProductionIssueLine> lines = lineMapper.selectList(
            Wrappers.lambdaQuery(WmProductionIssueLine.class)
                .eq(WmProductionIssueLine::getIssueId, issueId));

        if (lines == null || lines.isEmpty()) {
            throw new RuntimeException("生产领料单无明细行");
        }

        for (WmProductionIssueLine line : lines) {
            stockOperationService.postProductionIssue(line, new Date());
        }

        issue.setStatus("EXECUTED");
        baseMapper.updateById(issue);
        log.info("生产领料单执行完成, issueId={}", issueId);
    }

    private void insertOrUpdateLines(Long issueId, List<WmProductionIssueLine> lineList) {
        if (lineList != null && !lineList.isEmpty()) {
            for (WmProductionIssueLine line : lineList) {
                line.setLineId(null);
                line.setIssueId(issueId);
                lineMapper.insert(line);
            }
        }
    }

    private void deleteLinesByIssueId(Long issueId) {
        if (issueId != null) {
            LambdaQueryWrapper<WmProductionIssueLine> lqw = Wrappers.lambdaQuery();
            lqw.eq(WmProductionIssueLine::getIssueId, issueId);
            lineMapper.delete(lqw);
        }
    }

    private LambdaQueryWrapper<WmProductionIssue> buildQueryWrapper(WmProductionIssueBo bo) {
        LambdaQueryWrapper<WmProductionIssue> lqw = Wrappers.lambdaQuery();
        lqw.eq(StringUtils.isNotBlank(bo.getIssueCode()), WmProductionIssue::getIssueCode, bo.getIssueCode());
        lqw.eq(bo.getWoId() != null, WmProductionIssue::getWoId, bo.getWoId());
        lqw.eq(StringUtils.isNotBlank(bo.getWoNo()), WmProductionIssue::getWoNo, bo.getWoNo());
        lqw.eq(bo.getWarehouseId() != null, WmProductionIssue::getWarehouseId, bo.getWarehouseId());
        lqw.eq(StringUtils.isNotBlank(bo.getStatus()), WmProductionIssue::getStatus, bo.getStatus());
        lqw.orderByDesc(WmProductionIssue::getCreateTime);
        return lqw;
    }
}

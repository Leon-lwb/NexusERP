package com.nexus.workflow.service.impl;

import cn.hutool.core.util.ObjectUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nexus.common.core.page.WFTableDataInfo;
import com.nexus.common.utils.MapstructUtils;
import com.nexus.common.utils.StringUtils;
import com.nexus.common.utils.poi.ExcelUtil;
import com.nexus.common.core.page.PageQuery;
import com.nexus.common.core.page.TableDataInfo;
//import com.nexus.common.tenant.helper.TenantHelper;
import com.nexus.workflow.domain.WfCategory;
import com.nexus.workflow.domain.bo.WfCategoryBo;
import com.nexus.workflow.domain.vo.WfAppTypeVo;
import com.nexus.workflow.domain.vo.WfCategoryVo;
import com.nexus.workflow.mapper.WfCategoryMapper;
import com.nexus.workflow.service.IWfCategoryService;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * 流程分类Service业务层处理
 *
 * @author nbacheng
 * @date 2022-01-15
 */
@RequiredArgsConstructor
@Service
public class WfCategoryServiceImpl implements IWfCategoryService {

    private final WfCategoryMapper baseMapper;

    @Override
    public WfCategoryVo queryById(Long categoryId){
        return baseMapper.selectVoById(categoryId);
    }

    @Override
    public WFTableDataInfo<WfCategoryVo> queryPageList(WfCategoryBo categoryBo, PageQuery pageQuery) {
        LambdaQueryWrapper<WfCategory> lqw = buildQueryWrapper(categoryBo);
        lqw.eq(WfCategory::getDelFlag, "0");
        //lqw.eq(WfCategory::getTenantId, TenantHelper.getTenantId());
        Page<WfCategoryVo> result = baseMapper.selectVoPage(pageQuery.build(), lqw);
        return WFTableDataInfo.build(result);
    }

    @Override
    public List<WfCategoryVo> queryList(WfCategoryBo categoryBo) {
        LambdaQueryWrapper<WfCategory> lqw = buildQueryWrapper(categoryBo);
        lqw.eq(WfCategory::getDelFlag, "0");
        //lqw.eq(WfCategory::getTenantId, TenantHelper.getTenantId());
        return baseMapper.selectVoList(lqw);
    }

    private LambdaQueryWrapper<WfCategory> buildQueryWrapper(WfCategoryBo categoryBo) {
        Map<String, Object> params = categoryBo.getParams();
        LambdaQueryWrapper<WfCategory> lqw = Wrappers.lambdaQuery();
        lqw.like(StringUtils.isNotBlank(categoryBo.getCategoryName()), WfCategory::getCategoryName, categoryBo.getCategoryName());
        lqw.eq(StringUtils.isNotBlank(categoryBo.getCode()), WfCategory::getCode, categoryBo.getCode());
        return lqw;
    }

    @Override
    public int insertCategory(WfCategoryBo categoryBo) {
        WfCategory add = MapstructUtils.convert(categoryBo, WfCategory.class);
        //add.setTenantId(TenantHelper.getTenantId());
        return baseMapper.insert(add);
    }

    @Override
    public int updateCategory(WfCategoryBo categoryBo) {
        WfCategory update = MapstructUtils.convert(categoryBo, WfCategory.class);
        return baseMapper.updateById(update);
    }

    @Override
    public int deleteWithValidByIds(Collection<Long> ids, Boolean isValid) {
        if(isValid){
            //TODO 做一些业务上的校验,判断是否需要校验
        }
        return baseMapper.deleteBatchIds(ids);
    }

    /**
     * 校验分类编码是否唯一
     *
     * @param categoryBo 流程分类
     * @return 结果
     */
    @Override
    public boolean checkCategoryCodeUnique(WfCategoryBo categoryBo) {
        boolean exist = baseMapper.exists(new LambdaQueryWrapper<WfCategory>()
            .eq(WfCategory::getCode, categoryBo.getCode())
            //.eq(WfCategory::getTenantId, TenantHelper.getTenantId())
            .ne(ObjectUtil.isNotNull(categoryBo.getCategoryId()), WfCategory::getCategoryId, categoryBo.getCategoryId()));
        return !exist;
    }

	@Override
	public List<WfAppTypeVo> queryInfoByCode(@NotNull(message = "主键不能为空") String code) {
		WfAppTypeVo wfAppTypeVo = new WfAppTypeVo();
		//wfAppTypeVo = baseMapper.selectAppTypeVoByCode(code,TenantHelper.getTenantId());
		ArrayList<WfAppTypeVo> wfAppTypeVoList = new ArrayList<WfAppTypeVo>();
		wfAppTypeVoList.add(wfAppTypeVo);
		return wfAppTypeVoList;
	}
}

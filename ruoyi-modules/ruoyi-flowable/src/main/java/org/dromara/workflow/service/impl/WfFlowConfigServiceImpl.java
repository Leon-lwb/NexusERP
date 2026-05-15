package org.dromara.workflow.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.convert.Convert;

import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.generator.domain.GenTableColumn;
import org.dromara.generator.service.IGenTableService;
import org.dromara.system.domain.SysDictData;
import org.dromara.system.mapper.SysDictDataMapper;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;

import org.apache.commons.lang3.ObjectUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.dromara.workflow.domain.bo.WfFlowConfigBo;
import org.dromara.workflow.domain.vo.WfCustomFormVo;
import org.dromara.workflow.domain.vo.WfCustomRuleVo;
import org.dromara.workflow.domain.vo.WfFlowConfigVo;
import org.dromara.workflow.domain.vo.WfFormVo;
import org.dromara.workflow.domain.vo.WfOperateRuleVo;
import org.dromara.workflow.domain.vo.WfRuleVo;
import org.dromara.workflow.domain.bo.WfCustomRuleBo;
import org.dromara.workflow.domain.bo.WfOperateRuleBo;
import org.dromara.workflow.domain.WfFlowConfig;
import org.dromara.workflow.mapper.WfCustomRuleMapper;
import org.dromara.workflow.mapper.WfFlowConfigMapper;
import org.dromara.workflow.mapper.WfOperateRuleMapper;
import org.dromara.workflow.service.IWfCustomFormService;
import org.dromara.workflow.service.IWfCustomRuleService;
import org.dromara.workflow.service.IWfFlowConfigService;
import org.dromara.workflow.service.IWfFormService;
import org.dromara.workflow.service.IWfOperateRuleService;

import java.util.List;
import java.util.Map;
import java.util.ArrayList;


import java.util.Collection;

/**
 * 流程配置Service业务层处理
 *
 * @author wanghuan
 * @date 2023-11-19
 */
@RequiredArgsConstructor
@Service
public class WfFlowConfigServiceImpl implements IWfFlowConfigService {

    private final WfFlowConfigMapper baseMapper;
    private final WfCustomRuleMapper customRuleMapper;
    private final WfOperateRuleMapper operateRuleMapper;
    private final IWfCustomFormService customFormService;
    private final IGenTableService genTableService;
    private final IWfCustomRuleService customRuleService;
    private final IWfOperateRuleService operateRuleService;
    private final SysDictDataMapper sysDictDataMapper;
    private final IWfFormService formService;

    /**
     * 查询流程配置主
     */
    @Override
    public WfFlowConfigVo queryById(Long id){
        return baseMapper.selectVoById(id);
    }

    /**
     * 查询流程配置主列表
     */
    @Override
    public TableDataInfo<WfFlowConfigVo> queryPageList(WfFlowConfigBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<WfFlowConfig> lqw = buildQueryWrapper(bo);
        Page<WfFlowConfigVo> result = baseMapper.selectVoPage(pageQuery.build(), lqw);
        return TableDataInfo.build(result);
    }

    /**
     * 查询流程配置主列表
     */
    @Override
    public List<WfFlowConfigVo> queryList(WfFlowConfigBo bo) {
        LambdaQueryWrapper<WfFlowConfig> lqw = buildQueryWrapper(bo);
        return baseMapper.selectVoList(lqw);
    }

    private LambdaQueryWrapper<WfFlowConfig> buildQueryWrapper(WfFlowConfigBo bo) {
        Map<String, Object> params = bo.getParams();
        LambdaQueryWrapper<WfFlowConfig> lqw = Wrappers.lambdaQuery();
        lqw.eq(StringUtils.isNotBlank(bo.getModelId()), WfFlowConfig::getModelId, bo.getModelId());
        lqw.eq(StringUtils.isNotBlank(bo.getNodeKey()), WfFlowConfig::getNodeKey, bo.getNodeKey());
        lqw.like(StringUtils.isNotBlank(bo.getNodeName()), WfFlowConfig::getNodeName, bo.getNodeName());
        lqw.eq(StringUtils.isNotBlank(bo.getFormKey()), WfFlowConfig::getFormKey, bo.getFormKey());
        lqw.eq(StringUtils.isNotBlank(bo.getAppType()), WfFlowConfig::getAppType, bo.getAppType());
        return lqw;
    }

    /**
     * 新增流程配置主
     */
    @Override
    public Boolean insertByBo(WfFlowConfigBo bo) {
        WfFlowConfig add = BeanUtil.toBean(bo, WfFlowConfig.class);
        validEntityBeforeSave(add);
        boolean flag = baseMapper.insert(add) > 0;
        if (flag) {
            bo.setId(add.getId());
        }
        return flag;
    }

    /**
     * 修改流程配置主
     */
    @Override
    public Boolean updateByBo(WfFlowConfigBo bo) {
        WfFlowConfig update = BeanUtil.toBean(bo, WfFlowConfig.class);
        validEntityBeforeSave(update);
        return baseMapper.updateById(update) > 0;
    }

    /**
     * 保存前的数据校验
     */
    private void validEntityBeforeSave(WfFlowConfig entity){
        //TODO 做一些数据校验,如唯一约束
    }

    /**
     * 批量删除流程配置主
     */
    @Override
    public Boolean deleteWithValidByIds(Collection<Long> ids, Boolean isValid) {
        if(isValid){
            //TODO 做一些业务上的校验,判断是否需要校验
        }
        return baseMapper.deleteBatchIds(ids) > 0;
    }

	@Override
	public void updateFlowConfig(WfFlowConfigVo flowConfigVo) {
		baseMapper.updateFlowConfig(flowConfigVo);
	}

	@Override
	public WfFlowConfig selectByModelIdAndNodeKey(WfFlowConfigVo flowConfigVo) {
		return baseMapper.selectByModelIdAndNodeKey(flowConfigVo);
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public WfRuleVo queryConfigRule(WfFlowConfigBo bo) {

		WfRuleVo ruleVo = new WfRuleVo();
		//获取自定义表单规则列表
		if(bo.getAppType().equalsIgnoreCase("ZDYYW")) { //自定义业务
			List<WfCustomRuleVo> customRuleList = customRuleMapper.selectRuleByConfigId(bo.getId());
			if(ObjectUtils.isNotEmpty(customRuleList) && customRuleList.size()>0) {
				ruleVo.setCustomRuleVoList(customRuleList);
			}
			else {//为空添加默认表单规则设置
				if(StringUtils.isNotEmpty(bo.getFormKey())) {//获取自定义表信息
					Long formId = Convert.toLong(StringUtils.substringAfter(bo.getFormKey(), "key_"));
					WfCustomFormVo customFormVo = customFormService.queryById(formId);
					if(ObjectUtils.isNotEmpty(customFormVo)) {
						Long tableId = customFormVo.getTableId();
						List<GenTableColumn> tableColumnList = genTableService.selectGenTableColumnListByTableId(tableId);
						if(ObjectUtils.isNotEmpty(tableColumnList)) {
							long i = 0L;
							List<WfCustomRuleVo> customAddRuleList = new ArrayList<WfCustomRuleVo>();
							for(GenTableColumn tableColumn : tableColumnList) {
								WfCustomRuleBo customRuleBo = new WfCustomRuleBo();
								WfCustomRuleVo customRuleVo = new WfCustomRuleVo();
								customRuleBo.setColCode(tableColumn.getColumnName());
								customRuleBo.setColName(tableColumn.getColumnComment());
								customRuleBo.setConfigId(bo.getId());
								customRuleBo.setJavaField(tableColumn.getJavaField());
								customRuleBo.setJavaType(tableColumn.getJavaType());
								customRuleBo.setAttribute("1"); //默认只读
								i = i + 1;
								customRuleBo.setSort(i);
								customRuleService.insertByBo(customRuleBo);
								BeanUtils.copyProperties(customRuleBo, customRuleVo);
								customAddRuleList.add(customRuleVo);

							}
							ruleVo.setCustomRuleVoList(customAddRuleList);
						}
					}
				}
			}
		} else if(bo.getAppType().equalsIgnoreCase("OA")) {
			List<WfCustomRuleVo> customRuleList = customRuleMapper.selectRuleByConfigId(bo.getId());
			if(ObjectUtils.isNotEmpty(customRuleList) && customRuleList.size()>0) {
				ruleVo.setCustomRuleVoList(customRuleList);
			}
			else {//为空添加默认表单规则设置
				if(StringUtils.isNotEmpty(bo.getFormKey())) {//获取formdesigner表单信息
					Long formId = Convert.toLong(StringUtils.substringAfter(bo.getFormKey(), "key_"));
					WfFormVo formVo = formService.queryById(formId);
					if(ObjectUtils.isNotEmpty(formVo)) {
						List<WfCustomRuleVo> customAddRuleList = new ArrayList<WfCustomRuleVo>();
						JSONObject formObject = JSON.parseObject(formVo.getContent());
						JSONArray fieldArray = formObject.getJSONArray("list");
						for (int i = 0; i < fieldArray.size(); i++) {
							WfCustomRuleBo customRuleBo = new WfCustomRuleBo();
							WfCustomRuleVo customRuleVo = new WfCustomRuleVo();
							JSONObject fieldObject = fieldArray.getJSONObject(i);
							customRuleBo.setColCode(fieldObject.getString("id"));
							customRuleBo.setColName(fieldObject.getString("compName"));
							customRuleBo.setConfigId(bo.getId());
							customRuleBo.setAttribute("1"); //默认只读
							customRuleBo.setSort(Long.valueOf(i+1));
							customRuleService.insertByBo(customRuleBo);
							BeanUtils.copyProperties(customRuleBo, customRuleVo);
							customAddRuleList.add(customRuleVo);
						}
						ruleVo.setCustomRuleVoList(customAddRuleList);
					}
				}
			}
		}

		//获取操作规则列表
		List<WfOperateRuleVo> operateRuleList = operateRuleMapper.selectRuleByConfigId(bo.getId());
		if(ObjectUtils.isNotEmpty(operateRuleList) && operateRuleList.size()>0) {
			ruleVo.setOperateRuleVoList(operateRuleList);
		}
		else {//为空添加默认操作表单规则设置
			//从字典里获取操作类型
			List<SysDictData> sysDictDataList = sysDictDataMapper.selectDictDataListByDictType("wf_oper_type");
			if(ObjectUtils.isNotEmpty(sysDictDataList)) {
				long i = 0L;
				List<WfOperateRuleVo> operateAddRuleList = new ArrayList<WfOperateRuleVo>();
				for(SysDictData sysDictData : sysDictDataList) {
					WfOperateRuleBo operateRuleBo = new WfOperateRuleBo();
					WfOperateRuleVo operateRuleVo = new WfOperateRuleVo();
					operateRuleBo.setConfigId(bo.getId());
					operateRuleBo.setOpeType(sysDictData.getDictValue());
					operateRuleBo.setOpeName(sysDictData.getDictLabel());
					if(StringUtils.equalsAnyIgnoreCase(sysDictData.getDictValue(), "agree")    ||
					   StringUtils.equalsAnyIgnoreCase(sysDictData.getDictValue(), "delegate") ||
					   StringUtils.equalsAnyIgnoreCase(sysDictData.getDictValue(), "transfer") ||
					   StringUtils.equalsAnyIgnoreCase(sysDictData.getDictValue(), "reback")   ||
					   StringUtils.equalsAnyIgnoreCase(sysDictData.getDictValue(), "reject")) {
						operateRuleBo.setIsEnable("1"); //默认上面的操作开启
					}
					else {
						operateRuleBo.setIsEnable("0"); //其它默认关闭
					}
					i = i + 1;
					operateRuleBo.setSort(i);
					operateRuleService.insertByBo(operateRuleBo);
					BeanUtils.copyProperties(operateRuleBo, operateRuleVo);
					operateAddRuleList.add(operateRuleVo);

				}
				ruleVo.setOperateRuleVoList(operateAddRuleList);
			}
		}
		return ruleVo;
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public Boolean updateConfigRule(WfRuleVo vo) {
		List<WfCustomRuleVo> customRuleList = vo.getCustomRuleVoList();
		List<WfOperateRuleVo> operateRuleList = vo.getOperateRuleVoList();
		if(ObjectUtils.isNotEmpty(customRuleList) && ObjectUtils.isNotEmpty(operateRuleList) ) {
			for(WfCustomRuleVo customRuleVo : customRuleList) {
				WfCustomRuleBo customRuleBo = new WfCustomRuleBo();
				BeanUtils.copyProperties(customRuleVo,customRuleBo);
				customRuleService.updateByBo(customRuleBo);
			}
            for(WfOperateRuleVo operateRuleVo : operateRuleList) {
            	WfOperateRuleBo operateRuleBo = new WfOperateRuleBo();
            	BeanUtils.copyProperties(operateRuleVo,operateRuleBo);
            	operateRuleService.updateByBo(operateRuleBo);
			}
			return true;
		}

		return false;
	}
}

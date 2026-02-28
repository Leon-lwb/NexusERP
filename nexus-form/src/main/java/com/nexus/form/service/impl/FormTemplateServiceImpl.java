package com.nexus.form.service.impl;

import com.nexus.common.utils.DateUtils;
import com.nexus.form.domain.FormTemplate;
import com.nexus.form.mapper.FormTemplateMapper;
import com.nexus.form.service.IFormTemplateService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 单模板Service业务层处理
 * 
 * @author ruoyi
 * @date 2025-05-12
 */
@Service
public class FormTemplateServiceImpl implements IFormTemplateService 
{
    @Autowired
    private FormTemplateMapper formTemplateMapper;

    /**
     * 查询单模板
     * 
     * @param formId 单模板主键
     * @return 单模板
     */
    @Override
    public FormTemplate selectFormTemplateByFormId(Long formId)
    {
        return formTemplateMapper.selectFormTemplateByFormId(formId);
    }

    /**
     * 查询单模板列表
     * 
     * @param formTemplate 单模板
     * @return 单模板
     */
    @Override
    public List<FormTemplate> selectFormTemplateList(FormTemplate formTemplate)
    {
        return formTemplateMapper.selectFormTemplateList(formTemplate);
    }

    /**
     * 新增单模板
     * 
     * @param formTemplate 单模板
     * @return 结果
     */
    @Override
    public int insertFormTemplate(FormTemplate formTemplate)
    {
        formTemplate.setCreateTime(DateUtils.getNowDate());
        return formTemplateMapper.insertFormTemplate(formTemplate);
    }

    /**
     * 修改单模板
     * 
     * @param formTemplate 单模板
     * @return 结果
     */
    @Override
    public int updateFormTemplate(FormTemplate formTemplate)
    {
        formTemplate.setUpdateTime(DateUtils.getNowDate());
        return formTemplateMapper.updateFormTemplate(formTemplate);
    }

    /**
     * 批量删除单模板
     * 
     * @param formIds 需要删除的单模板主键
     * @return 结果
     */
    @Override
    public int deleteFormTemplateByFormIds(Long[] formIds)
    {
        return formTemplateMapper.deleteFormTemplateByFormIds(formIds);
    }

    /**
     * 删除单模板信息
     * 
     * @param formId 单模板主键
     * @return 结果
     */
    @Override
    public int deleteFormTemplateByFormId(Long formId)
    {
        return formTemplateMapper.deleteFormTemplateByFormId(formId);
    }
}

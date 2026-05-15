package org.dromara.workflow.service.impl;

import cn.hutool.core.util.StrUtil;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.dromara.common.core.utils.SpringContextHolder;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.common.tenant.helper.TenantHelper;
import org.dromara.workflow.domain.WfForm;
import org.dromara.workflow.domain.bo.WfFormBo;
import org.dromara.workflow.domain.vo.WfFormVo;
import org.dromara.workflow.mapper.WfFormMapper;
import org.dromara.workflow.service.IWfFormService;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import javax.sql.DataSource;
import java.sql.Statement;

/**
 * 流程表单Service业务层处理
 *
 * @author wanghuan
 * @createTime 2022/3/7 22:07
 */
@RequiredArgsConstructor
@Service
public class WfFormServiceImpl implements IWfFormService {

    private final WfFormMapper baseMapper;

    /**
     * 查询流程表单
     *
     * @param formId 流程表单ID
     * @return 流程表单
     */
    @Override
    public WfFormVo queryById(Long formId) {
        return baseMapper.selectVoById(formId);
    }

    /**
     * 查询流程表单列表
     *
     * @param bo 流程表单
     * @return 流程表单
     */
    @Override
    public TableDataInfo<WfFormVo> queryPageList(WfFormBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<WfForm> lqw = buildQueryWrapper(bo);
        lqw.eq(WfForm::getDelFlag, "0");
        lqw.eq(WfForm::getTenantId, TenantHelper.getTenantId());
        Page<WfFormVo> result = baseMapper.selectVoPage(pageQuery.build(), lqw);
        return TableDataInfo.build(result);
    }

    /**
     * 查询流程表单列表
     *
     * @param bo 流程表单
     * @return 流程表单
     */
    @Override
    public List<WfFormVo> queryList(WfFormBo bo) {
        LambdaQueryWrapper<WfForm> lqw = buildQueryWrapper(bo);
        return baseMapper.selectVoList(lqw);
    }

    /**
     * 新增流程表单
     *
     * @param bo 流程表单
     * @return 结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insertForm(WfFormBo bo) {
        WfForm wfForm = new WfForm();
        wfForm.setFormName(bo.getFormName());
        wfForm.setContent(bo.getContent());
        wfForm.setRemark(bo.getRemark());
        wfForm.setTenantId(TenantHelper.getTenantId());
        wfForm.setDb(bo.getDb());
        wfForm.setTableName(bo.getTableName());
        if (bo.getDb()) {
	        try {
				saveVForm3ToDb(bo.getContent(), bo.getFormName(), bo.getTableName());
			} catch (SQLException e) {
				throw new RuntimeException(e.getMessage());
			}
        }

        return baseMapper.insert(wfForm);
    }

    /**
     * 修改流程表单
     *
     * @param bo 流程表单
     * @return 结果
     */
    @Override
    public int updateForm(WfFormBo bo) {
    	if (bo.getDb()) {
    		if (StringUtils.isNotEmpty(bo.getTableName())) {
        		int rows = baseMapper.getRows(bo.getTableName());
        		if (rows > 0) {
        			return 0;
        		}
        		else {
        			String createTableQuery = "DROP TABLE " + bo.getTableName();
        			DataSource dataSource = SpringContextHolder.getApplicationContext().getBean(DataSource.class);
        			Connection connection = null;
    				try {
    					connection = dataSource.getConnection();
    					Statement statement = connection.createStatement();
        				statement.execute(createTableQuery);
        				saveVForm3ToDb(bo.getContent(), bo.getFormName(), bo.getTableName());
    				} catch (SQLException e) {
    					throw new RuntimeException(e.getMessage());
    				} finally {
        	            try {
    						connection.close();
    					} catch (SQLException e) {
    						throw new RuntimeException(e.getMessage());
    					}
        	        }
        		}
        	}
    	}

        return baseMapper.update(new WfForm(), new LambdaUpdateWrapper<WfForm>()
            .set(StrUtil.isNotBlank(bo.getFormName()), WfForm::getFormName, bo.getFormName())
            .set(StrUtil.isNotBlank(bo.getContent()), WfForm::getContent, bo.getContent())
            .set(StrUtil.isNotBlank(bo.getRemark()), WfForm::getRemark, bo.getRemark())
            .eq(WfForm::getFormId, bo.getFormId()));
    }

    /**
     * 批量删除流程表单
     *
     * @param ids 需要删除的流程表单ID
     * @return 结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean deleteWithValidByIds(Collection<Long> ids) {
    	ids.forEach(id -> {
    	    // 在这里处理每个id
    		String tableName = baseMapper.selectTableName(id);
    		if(StringUtils.isNotEmpty(tableName)) {
    			String createTableQuery = "DROP TABLE " + tableName;
    			DataSource dataSource = SpringContextHolder.getApplicationContext().getBean(DataSource.class);
    			Connection connection = null;
				try {
					connection = dataSource.getConnection();
					Statement statement = connection.createStatement();
    				statement.execute(createTableQuery);
				} catch (SQLException e) {
					throw new RuntimeException(e.getMessage());
				} finally {
    	            try {
						connection.close();
					} catch (SQLException e) {
						throw new RuntimeException(e.getMessage());
					}
    	        }
    		}

    	});
        return baseMapper.deleteBatchIds(ids) > 0;
    }

    private LambdaQueryWrapper<WfForm> buildQueryWrapper(WfFormBo bo) {
        Map<String, Object> params = bo.getParams();
        LambdaQueryWrapper<WfForm> lqw = Wrappers.lambdaQuery();
        lqw.like(StringUtils.isNotBlank(bo.getFormName()), WfForm::getFormName, bo.getFormName());
        return lqw;
    }

    /**
     * 建VForm3表单表
     *
     * @param content 表单内容
     * @return
     * @return 结果
     * @throws SQLException
     */

    private void saveVForm3ToDb(String content , String formName, String tableName) throws SQLException {
    	JSONObject formObject = JSON.parseObject(content);
		JSONArray fieldArray = formObject.getJSONArray("widgetList");
		List<String> fieldSqlList = new ArrayList<String>();
		for (int i = 0; i < fieldArray.size(); i++) {
			JSONObject fieldObject = fieldArray.getJSONObject(i);
			String fieldSqlType = getFieldSqlType(fieldObject);
			if(StringUtils.isNotEmpty(fieldSqlType)) {
				fieldSqlList.add(fieldSqlType);
			}
		}
		String createTableQuery = "CREATE TABLE IF NOT EXISTS " + tableName
				+ " (id bigint(20) NOT NULL COMMENT '编号',"
				+ " tenant_id varchar(20) DEFAULT '000000' COMMENT '租户编号',"
				+ String.join(", ", fieldSqlList) + ", PRIMARY KEY (id)) COMMENT='" + formName +"'";
		DataSource dataSource = SpringContextHolder.getApplicationContext().getBean(DataSource.class);
		Connection connection = dataSource.getConnection();
		try {
			Statement statement = connection.createStatement();
			statement.execute(createTableQuery);
		} catch (SQLException e) {
			throw new RuntimeException(e.getMessage());
		} finally {
            connection.close();
        }
    }

    /**
     * 获取VForm3字段的sql信息
     *
     * @param vformField VForm3字段
     * @return 结果
     */
    private  String getFieldSqlType(JSONObject vformField) {
    	String fieldSqlType = "";
    	String type = vformField.getString("type");
    	JSONObject optionsObj = vformField.getJSONObject("options");

        switch (type) {
            case "input":
            	fieldSqlType = optionsObj.getString("name") + " "  + "varchar(100)" + " "
                               + "DEFAULT" + " '" + optionsObj.get("defaultValue") + "' "
                               + "COMMENT" + " '" + optionsObj.getString("label") + "'";
            	break;
            case "textarea":
            	fieldSqlType = optionsObj.getString("name") + " "  + "text" + " "
                        + "COMMENT" + " '" + optionsObj.getString("label") + "'";
            	break;
            case "number":
            	fieldSqlType = optionsObj.getString("name") + " "  + "int" + " "
                        + "DEFAULT" + " " + optionsObj.get("defaultValue") + " "
                        + "COMMENT" + " '" + optionsObj.getString("label") + "'";
            	break;
            case "date":
            	fieldSqlType = optionsObj.getString("name") + " "  + "date" + " "
                        + "DEFAULT" + " " + optionsObj.get("defaultValue") + " "
                        + "COMMENT" + " '" + optionsObj.getString("label") + "'";
            	break;
            case "date-range":
            	fieldSqlType = optionsObj.getString("name") + " "  + "varchar(30)" + " "
                        + "COMMENT" + " '" + optionsObj.getString("label") + "'";
            	break;
            case "datetime":
            	fieldSqlType = optionsObj.getString("name") + " "  + "datetime" + " "
                        + "DEFAULT" + " " + optionsObj.get("defaultValue") + " "
                        + "COMMENT" + " '" + optionsObj.getString("label") + "'";
            	break;
            case "time":
            	fieldSqlType = optionsObj.getString("name") + " "  + "time" + " "
                        + "DEFAULT" + " " + optionsObj.get("defaultValue") + " "
                        + "COMMENT" + " '" + optionsObj.getString("label") + "'";
            	break;
            case "time-range":
            	fieldSqlType = optionsObj.getString("name") + " "  + "varchar(20)" + " "
                        + "COMMENT" + " '" + optionsObj.getString("label") + "'";
            	break;
            case "radio":
            	fieldSqlType = optionsObj.getString("name") + " "  + "tinyint" + " "
                        + "DEFAULT" + " " + optionsObj.get("defaultValue") + " "
                        + "COMMENT" + " '" + optionsObj.getString("label") + "'";
            	break;
            case "checkbox":
            	fieldSqlType = optionsObj.getString("name") + " "  + "varchar(100)" + " "
                        + "COMMENT" + " '" + optionsObj.getString("label") + "'";
            	break;
            case "select":
            	fieldSqlType = optionsObj.getString("name") + " "  + "tinyint" + " "
                        + "COMMENT" + " '" + optionsObj.getString("label") + "'";
            	break;
            case "switch":
            	short defaultValue =  (short) ((boolean) optionsObj.get("defaultValue")?1:0);
            	fieldSqlType = optionsObj.getString("name") + " "  + "tinyint" + " "
                        + "DEFAULT" + " " + defaultValue + " "
                        + "COMMENT" + " '" + optionsObj.getString("label") + "'";
            	break;
            case "rate":
            	fieldSqlType = optionsObj.getString("name") + " "  + "tinyint" + " "
                        + "DEFAULT" + " " + optionsObj.get("defaultValue") + " "
                        + "COMMENT" + " '" + optionsObj.getString("label") + "'";
            	break;
            case "color":
            	fieldSqlType = optionsObj.getString("name") + " "  + "varchar(10)" + " "
                        + "DEFAULT" + " " + optionsObj.get("defaultValue") + " "
                        + "COMMENT" + " '" + optionsObj.getString("label") + "'";
            	break;
            case "slider":
            	fieldSqlType = optionsObj.getString("name") + " "  + "tinyint" + " "
                        + "DEFAULT" + " " + optionsObj.get("defaultValue") + " "
                        + "COMMENT" + " '" + optionsObj.getString("label") + "'";
            	break;
            case "picture-upload":
            	fieldSqlType = optionsObj.getString("name") + " "  + "varchar(255)" + " "
                        + "COMMENT" + " '" + optionsObj.getString("label") + "'";
            	break;
            case "file-upload":
            	fieldSqlType = optionsObj.getString("name") + " "  + "varchar(255)" + " "
                        + "COMMENT" + " '" + optionsObj.getString("label") + "'";
            	break;
            case "rich-editor":
            	fieldSqlType = optionsObj.getString("name") + " "  + "longtext" + " "
                        + "COMMENT" + " '" + optionsObj.getString("label") + "'";
            	break;
            case "cascader":
            	fieldSqlType = optionsObj.getString("name") + " "  + "varchar(100)" + " "
                        + "COMMENT" + " '" + optionsObj.getString("label") + "'";
            	break;
        }
        return fieldSqlType;
    }

}

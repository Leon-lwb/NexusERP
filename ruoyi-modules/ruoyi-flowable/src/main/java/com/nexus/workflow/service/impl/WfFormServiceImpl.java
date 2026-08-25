package com.nexus.workflow.service.impl;

import cn.hutool.core.util.StrUtil;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.core.utils.SpringContextHolder;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.common.tenant.helper.TenantHelper;
import com.nexus.workflow.domain.WfForm;
import com.nexus.workflow.domain.bo.WfFormBo;
import com.nexus.workflow.domain.vo.WfFormVo;
import com.nexus.workflow.mapper.WfFormMapper;
import com.nexus.workflow.service.IWfFormService;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import javax.sql.DataSource;

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
     * 动态建表允许的表名/字段名：字母开头，仅含字母数字下划线，长度不超过 64（MySQL 标识符上限）
     */
    private static final Pattern IDENTIFIER_PATTERN = Pattern.compile("^[A-Za-z][A-Za-z0-9_]{0,63}$");

    /**
     * 数值型默认值
     */
    private static final Pattern NUMERIC_PATTERN = Pattern.compile("^-?\\d+(\\.\\d+)?$");

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
        if (Boolean.TRUE.equals(bo.getDb())) {
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
    	if (Boolean.TRUE.equals(bo.getDb()) && StringUtils.isNotEmpty(bo.getTableName())) {
    		// 表名白名单校验后再参与 SQL 拼接，防止 DDL 注入
    		String tableName = validateIdentifier(bo.getTableName());
    		int rows = baseMapper.getRows(tableName);
    		if (rows > 0) {
    			return 0;
    		}
    		else {
    			dropTable(tableName);
    			try {
    				saveVForm3ToDb(bo.getContent(), bo.getFormName(), tableName);
    			} catch (SQLException e) {
    				throw new RuntimeException(e.getMessage());
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
    			dropTable(tableName);
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
     * @param formName 表单名称（用作表 COMMENT）
     * @param tableName 动态创建的物理表名
     * @throws SQLException 建表失败
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
		// 表名/字段名/COMMENT 均来自前端提交内容，必须校验与转义后再拼接 DDL，防止 SQL 注入
		String createTableQuery = "CREATE TABLE IF NOT EXISTS `" + validateIdentifier(tableName) + "`"
				+ " (`id` bigint(20) NOT NULL COMMENT '编号',"
				+ " `tenant_id` varchar(20) DEFAULT '000000' COMMENT '租户编号',"
				+ String.join(", ", fieldSqlList) + ", PRIMARY KEY (`id`)) COMMENT='" + escapeLiteral(formName) + "'";
		DataSource dataSource = SpringContextHolder.getApplicationContext().getBean(DataSource.class);
		try (Connection connection = dataSource.getConnection();
			 Statement statement = connection.createStatement()) {
			statement.execute(createTableQuery);
		}
	}

    /**
     * 删除动态表单对应的物理表
     *
     * @param tableName 物理表名（入库前已校验，此处再校验一次防御脏数据）
     */
    private void dropTable(String tableName) {
		String dropTableQuery = "DROP TABLE IF EXISTS `" + validateIdentifier(tableName) + "`";
		DataSource dataSource = SpringContextHolder.getApplicationContext().getBean(DataSource.class);
		try (Connection connection = dataSource.getConnection();
			 Statement statement = connection.createStatement()) {
			statement.execute(dropTableQuery);
		} catch (SQLException e) {
			throw new RuntimeException(e.getMessage());
		}
	}

    /**
     * 校验动态表单的表名/字段名，仅允许字母开头的字母数字下划线且长度不超过 64，
     * 不满足则拒绝执行 DDL，防止 "name; DROP TABLE ..." 一类注入
     */
    private static String validateIdentifier(String identifier) {
        if (identifier == null || !IDENTIFIER_PATTERN.matcher(identifier).matches()) {
            throw new ServiceException("非法的表名/字段名：" + identifier);
        }
        return identifier;
    }

    /**
     * SQL 字符串字面量转义（COMMENT / DEFAULT 值），转义反斜杠与单引号
     */
    private static String escapeLiteral(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("'", "''");
    }

    /**
     * 获取VForm3字段的sql信息
     *
     * @param vformField VForm3字段
     * @return 结果
     */
    private String getFieldSqlType(JSONObject vformField) {
    	String type = vformField.getString("type");
    	JSONObject optionsObj = vformField.getJSONObject("options");
    	if (type == null || optionsObj == null) {
    		return "";
    	}
    	String name = optionsObj.getString("name");
    	String label = optionsObj.getString("label");
    	Object defaultValue = optionsObj.get("defaultValue");

        switch (type) {
            case "input":
            	return buildColumnSql(name, "varchar(100)", defaultValue, true, label);
            case "textarea":
            	return buildColumnSql(name, "text", null, false, label);
            case "number":
            	return buildColumnSql(name, "int", defaultValue, false, label);
            case "date":
            	return buildColumnSql(name, "date", defaultValue, true, label);
            case "date-range":
            	return buildColumnSql(name, "varchar(30)", null, false, label);
            case "datetime":
            	return buildColumnSql(name, "datetime", defaultValue, true, label);
            case "time":
            	return buildColumnSql(name, "time", defaultValue, true, label);
            case "time-range":
            	return buildColumnSql(name, "varchar(20)", null, false, label);
            case "radio":
            	return buildColumnSql(name, "tinyint", defaultValue, false, label);
            case "checkbox":
            	return buildColumnSql(name, "varchar(100)", null, false, label);
            case "select":
            	return buildColumnSql(name, "tinyint", null, false, label);
            case "switch":
            	return buildColumnSql(name, "tinyint", Boolean.TRUE.equals(defaultValue) ? 1 : 0, false, label);
            case "rate":
            	return buildColumnSql(name, "tinyint", defaultValue, false, label);
            case "color":
            	return buildColumnSql(name, "varchar(10)", defaultValue, true, label);
            case "slider":
            	return buildColumnSql(name, "tinyint", defaultValue, false, label);
            case "picture-upload":
            	return buildColumnSql(name, "varchar(255)", null, false, label);
            case "file-upload":
            	return buildColumnSql(name, "varchar(255)", null, false, label);
            case "rich-editor":
            	return buildColumnSql(name, "longtext", null, false, label);
            case "cascader":
            	return buildColumnSql(name, "varchar(100)", null, false, label);
            default:
            	return "";
        }
	}

    /**
     * 拼接单列 DDL：字段名经白名单校验并加反引号；DEFAULT 值仅接受转义后的
     * 字符串字面量或纯数字；COMMENT 一律转义，防止经表单 JSON 注入 SQL
     *
     * @param name 字段名
     * @param sqlType 字段类型
     * @param defaultValue 默认值（null 表示不设置）
     * @param quotedDefault 默认值是否按字符串字面量处理
     * @param label 字段中文名（表 COMMENT）
     */
    private static String buildColumnSql(String name, String sqlType, Object defaultValue, boolean quotedDefault, String label) {
    	String column = "`" + validateIdentifier(name) + "` " + sqlType;
    	String value = defaultValue == null ? null : String.valueOf(defaultValue);
    	if (StringUtils.isNotEmpty(value) && !"null".equals(value)) {
    		if (quotedDefault) {
    			column += " DEFAULT '" + escapeLiteral(value) + "'";
    		} else if (NUMERIC_PATTERN.matcher(value).matches()) {
    			column += " DEFAULT " + value;
    		}
    		// 数值型默认值非法时放弃 DEFAULT 子句，保留建表能力
    	}
    	if (StringUtils.isNotEmpty(label)) {
    		column += " COMMENT '" + escapeLiteral(label) + "'";
    	}
    	return column;
    }

}

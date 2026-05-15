package org.dromara.workflow.mapper;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.toolkit.Constants;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;
import org.dromara.workflow.domain.WfForm;
import org.dromara.workflow.domain.vo.WfFormVo;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 流程表单Mapper接口
 *
 * @author wanghuan
 * @createTime 2022/3/7 22:07
 */
public interface WfFormMapper extends BaseMapperPlus<WfForm, WfFormVo> {

    List<WfFormVo> selectFormVoList(@Param(Constants.WRAPPER) Wrapper<WfForm> queryWrapper);

    //根据VForm3的form_id获取创建的表名
    @Select("SELECT table_name FROM wf_form WHERE form_id = #{formId}")
    String selectTableName(@Param("formId") Long formId);

    //根据表名查询记录数
    @Select("SELECT count(*) FROM ${tableName}")
    int getRows(@Param("tableName") String tableName);
}

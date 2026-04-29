package org.dromara.workflow.mapper;

import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;
import org.dromara.workflow.domain.WfDemo;
import org.dromara.workflow.domain.vo.WfDemoVo;

import org.apache.ibatis.annotations.Param;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Constants;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
/**
 * DEMOMapper接口
 *
 * @author nbacheng
 * @date 2023-10-12
 */
public interface WfDemoMapper extends BaseMapperPlus<WfDemo, WfDemoVo> {

	Page<WfDemoVo> myPage(Page<WfDemoVo> page, @Param(Constants.WRAPPER) QueryWrapper<WfDemoVo> queryWrapper);

}

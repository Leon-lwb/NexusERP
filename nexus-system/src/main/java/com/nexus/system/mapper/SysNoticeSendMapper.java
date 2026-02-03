package com.nexus.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nexus.system.domain.SysNoticeSend;
import com.nexus.system.domain.vo.SysNoticeSendVo;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 用户公告阅读标记Mapper接口
 *
 * @author nbacheng
 * @date 2023-09-21
 */
public interface SysNoticeSendMapper {//extends BaseMapperPlus<SysNoticeSend, SysNoticeSendVo> {

	// List<NoticeSendModel> getMyNoticeSendList(Page<NoticeSendModel> pageList, @Param("noticeSendModel")NoticeSendModel noticeSendModel);

}

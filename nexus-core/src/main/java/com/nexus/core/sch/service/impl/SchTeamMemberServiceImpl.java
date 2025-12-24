package com.nexus.core.sch.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import java.util.List;
import com.nexus.common.utils.DateUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.nexus.core.sch.mapper.SchTeamMemberMapper;
import com.nexus.core.sch.domain.SchTeamMember;
import com.nexus.core.sch.service.ISchTeamMemberService;

/**
 * 班组成员Service业务层处理
 *
 * @author nexus
 * @date 2025-12-24
 */
@Service
public class SchTeamMemberServiceImpl extends ServiceImpl<SchTeamMemberMapper, SchTeamMember> implements ISchTeamMemberService {

    @Autowired
    private SchTeamMemberMapper schTeamMemberMapper;

    /**
     * 查询班组成员
     *
     * @param memberId 班组成员主键
     * @return 班组成员
     */
    @Override
    public SchTeamMember selectSchTeamMemberByMemberId(Long memberId) {
        return schTeamMemberMapper.selectSchTeamMemberByMemberId(memberId);
    }

    /**
     * 查询班组成员列表
     *
     * @param schTeamMember 班组成员
     * @return 班组成员
     */
    @Override
    public List<SchTeamMember> selectSchTeamMemberList(SchTeamMember schTeamMember) {
        return schTeamMemberMapper.selectSchTeamMemberList(schTeamMember);
    }

    /**
     * 新增班组成员
     *
     * @param schTeamMember 班组成员
     * @return 结果
     */
    @Override
    public int insertSchTeamMember(SchTeamMember schTeamMember) {
                schTeamMember.setCreateTime(DateUtils.getNowDate());
            return schTeamMemberMapper.insertSchTeamMember(schTeamMember);
    }

    /**
     * 修改班组成员
     *
     * @param schTeamMember 班组成员
     * @return 结果
     */
    @Override
    public int updateSchTeamMember(SchTeamMember schTeamMember) {
                schTeamMember.setUpdateTime(DateUtils.getNowDate());
        return schTeamMemberMapper.updateSchTeamMember(schTeamMember);
    }

    /**
     * 批量删除班组成员
     *
     * @param memberIds 需要删除的班组成员主键
     * @return 结果
     */
    @Override
    public int deleteSchTeamMemberByMemberIds(Long[] memberIds) {
        return schTeamMemberMapper.deleteSchTeamMemberByMemberIds(memberIds);
    }

    /**
     * 删除班组成员信息
     *
     * @param memberId 班组成员主键
     * @return 结果
     */
    @Override
    public int deleteSchTeamMemberByMemberId(Long memberId) {
        return schTeamMemberMapper.deleteSchTeamMemberByMemberId(memberId);
    }
}

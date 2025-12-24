package com.nexus.core.sch.service;

import java.util.List;
import com.nexus.core.sch.domain.SchTeamMember;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * 班组成员Service接口
 *
 * @author nexus
 * @date 2025-12-24
 */
public interface ISchTeamMemberService extends IService<SchTeamMember> {
    /**
     * 查询班组成员
     *
     * @param memberId 班组成员主键
     * @return 班组成员
     */
    public SchTeamMember selectSchTeamMemberByMemberId(Long memberId);

    /**
     * 查询班组成员列表
     *
     * @param schTeamMember 班组成员
     * @return 班组成员集合
     */
    public List<SchTeamMember> selectSchTeamMemberList(SchTeamMember schTeamMember);

    /**
     * 新增班组成员
     *
     * @param schTeamMember 班组成员
     * @return 结果
     */
    public int insertSchTeamMember(SchTeamMember schTeamMember);

    /**
     * 修改班组成员
     *
     * @param schTeamMember 班组成员
     * @return 结果
     */
    public int updateSchTeamMember(SchTeamMember schTeamMember);

    /**
     * 批量删除班组成员
     *
     * @param memberIds 需要删除的班组成员主键集合
     * @return 结果
     */
    public int deleteSchTeamMemberByMemberIds(Long[] memberIds);

    /**
     * 删除班组成员信息
     *
     * @param memberId 班组成员主键
     * @return 结果
     */
    public int deleteSchTeamMemberByMemberId(Long memberId);
}

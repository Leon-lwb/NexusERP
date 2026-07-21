package com.nexus.system.controller.system;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.hutool.core.lang.tree.Tree;
import cn.hutool.core.util.ObjectUtil;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import com.nexus.common.core.constant.SystemConstants;
import com.nexus.common.core.domain.R;
import com.nexus.common.excel.utils.ExcelUtil;
import com.nexus.common.idempotent.annotation.RepeatSubmit;
import com.nexus.common.log.annotation.Log;
import com.nexus.common.log.enums.BusinessType;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.common.web.core.BaseController;
import com.nexus.system.domain.bo.SysDeptBo;
import com.nexus.system.domain.bo.SysPostBo;
import com.nexus.system.domain.vo.SysPostVo;
import com.nexus.system.service.ISysDeptService;
import com.nexus.system.service.ISysPostService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 岗位管理Controller
 * <p>提供岗位的分页查询、Excel 导出、详情、增删改、岗位选择框及部门树查询等接口。
 * 属于系统管理（System）模块。</p>
 *
 * @author Lion Li
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/system/post")
public class SysPostController extends BaseController {

    /** 岗位业务服务，提供分页、唯一性校验、增删改等能力。 */
    private final ISysPostService postService;
    /** 部门业务服务，用于查询岗位选择时所用的部门树。 */
    private final ISysDeptService deptService;

    /**
     * 分页查询岗位列表。
     *
     * @param post      岗位查询条件（岗位名称、编码、状态等）
     * @param pageQuery 分页查询参数，包含页码、每页大小及排序条件
     * @return 岗位分页结果
     */
    @SaCheckPermission("system:post:list")
    @GetMapping("/list")
    public TableDataInfo<SysPostVo> list(SysPostBo post, PageQuery pageQuery) {
        return postService.selectPagePostList(post, pageQuery);
    }

    /**
     * 导出岗位列表为 Excel 文件。
     *
     * @param post     岗位查询条件
     * @param response HTTP 响应，用于写入导出的 Excel 流
     */
    @Log(title = "岗位管理", businessType = BusinessType.EXPORT)
    @SaCheckPermission("system:post:export")
    @PostMapping("/export")
    public void export(SysPostBo post, HttpServletResponse response) {
        List<SysPostVo> list = postService.selectPostList(post);
        ExcelUtil.exportExcel(list, "岗位数据", SysPostVo.class, response);
    }

    /**
     * 根据岗位编号查询岗位详细信息。
     *
     * @param postId 岗位 ID
     * @return 岗位详情
     */
    @SaCheckPermission("system:post:query")
    @GetMapping(value = "/{postId}")
    public R<SysPostVo> getInfo(@PathVariable Long postId) {
        return R.ok(postService.selectPostById(postId));
    }

    /**
     * 新增岗位，校验岗位名称与编码唯一性。
     *
     * @param post 岗位新增参数
     * @return 操作结果，成功时返回成功状态
     */
    @SaCheckPermission("system:post:add")
    @Log(title = "岗位管理", businessType = BusinessType.INSERT)
    @RepeatSubmit()
    @PostMapping
    public R<Void> add(@Validated @RequestBody SysPostBo post) {
        if (!postService.checkPostNameUnique(post)) {
            return R.fail("新增岗位'" + post.getPostName() + "'失败，岗位名称已存在");
        } else if (!postService.checkPostCodeUnique(post)) {
            return R.fail("新增岗位'" + post.getPostName() + "'失败，岗位编码已存在");
        }
        return toAjax(postService.insertPost(post));
    }

    /**
     * 修改岗位，校验名称、编码唯一性及停用约束。
     *
     * @param post 岗位修改参数
     * @return 操作结果，成功时返回成功状态
     */
    @SaCheckPermission("system:post:edit")
    @Log(title = "岗位管理", businessType = BusinessType.UPDATE)
    @RepeatSubmit()
    @PutMapping
    public R<Void> edit(@Validated @RequestBody SysPostBo post) {
        if (!postService.checkPostNameUnique(post)) {
            return R.fail("修改岗位'" + post.getPostName() + "'失败，岗位名称已存在");
        } else if (!postService.checkPostCodeUnique(post)) {
            return R.fail("修改岗位'" + post.getPostName() + "'失败，岗位编码已存在");
        } else if (SystemConstants.DISABLE.equals(post.getStatus())
            && postService.countUserPostById(post.getPostId()) > 0) {
            return R.fail("该岗位下存在已分配用户，不能禁用!");
        }
        return toAjax(postService.updatePost(post));
    }

    /**
     * 批量删除岗位。
     *
     * @param postIds 岗位 ID 数组
     * @return 操作结果，成功时返回成功状态
     */
    @SaCheckPermission("system:post:remove")
    @Log(title = "岗位管理", businessType = BusinessType.DELETE)
    @DeleteMapping("/{postIds}")
    public R<Void> remove(@PathVariable Long[] postIds) {
        return toAjax(postService.deletePostByIds(Arrays.asList(postIds)));
    }

    /**
     * 获取岗位选择框列表，支持按部门或岗位 ID 串过滤。
     *
     * @param postIds 预选岗位 ID 数组（为空则按部门过滤）
     * @param deptId  部门 ID（用于查询该部门下的岗位）
     * @return 岗位列表
     */
    @SaCheckPermission("system:post:query")
    @GetMapping("/optionselect")
    public R<List<SysPostVo>> optionselect(@RequestParam(required = false) Long[] postIds, @RequestParam(required = false) Long deptId) {
        List<SysPostVo> list = new ArrayList<>();
        if (ObjectUtil.isNotNull(deptId)) {
            SysPostBo post = new SysPostBo();
            post.setDeptId(deptId);
            list = postService.selectPostList(post);
        } else if (postIds != null) {
            list = postService.selectPostByIds(List.of(postIds));
        }
        return R.ok(list);
    }

    /**
     * 获取部门树列表（用于岗位选择所属部门）。
     *
     * @param dept 部门查询条件
     * @return 部门树
     */
    @SaCheckPermission("system:post:list")
    @GetMapping("/deptTree")
    public R<List<Tree<Long>>> deptTree(SysDeptBo dept) {
        return R.ok(deptService.selectDeptTreeList(dept));
    }


}

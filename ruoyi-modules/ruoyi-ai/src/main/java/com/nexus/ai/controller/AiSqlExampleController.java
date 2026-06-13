package com.nexus.ai.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.nexus.ai.service.query.SqlExampleService;
import com.nexus.common.ai.domain.bo.AiSqlExampleBo;
import com.nexus.common.ai.domain.vo.AiSqlExampleVo;
import com.nexus.common.ai.service.IAiSqlExampleService;
import com.nexus.common.core.domain.R;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.common.web.core.BaseController;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * AI SQL Few-Shot 示例管理接口
 * <p>提供 SQL 示例的 CRUD 操作，供管理员维护 Agent 参考的查询示例</p>
 *
 * @author NexusERP
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/ai/sql-example")
public class AiSqlExampleController extends BaseController {

    private final IAiSqlExampleService sqlExampleService;
    private final SqlExampleService sqlExampleRenderingService;

    /**
     * 分页查询 SQL 示例列表
     */
    @SaCheckPermission("ai:sqlExample:list")
    @GetMapping("/list")
    public TableDataInfo<AiSqlExampleVo> list(AiSqlExampleBo bo, PageQuery pageQuery) {
        return sqlExampleService.selectPageList(bo, pageQuery);
    }

    /**
     * 查询全部 SQL 示例（不分页）
     */
    @SaCheckPermission("ai:sqlExample:list")
    @GetMapping("/listAll")
    public List<AiSqlExampleVo> listAll(AiSqlExampleBo bo) {
        return sqlExampleService.selectList(bo);
    }

    /**
     * 获取 SQL 示例详情
     *
     * @param id 示例ID
     * @return 示例详情
     */
    @SaCheckPermission("ai:sqlExample:query")
    @GetMapping("/{id}")
    public R<AiSqlExampleVo> getInfo(@PathVariable Long id) {
        return R.ok(sqlExampleService.selectById(id));
    }

    /**
     * 新增 SQL 示例
     * <p>新增后清除渲染缓存，确保 Agent 使用最新示例</p>
     */
    @SaCheckPermission("ai:sqlExample:add")
    @PostMapping
    public R<Void> add(@Validated @RequestBody AiSqlExampleBo bo) {
        int result = sqlExampleService.insertByBo(bo);
        if (result > 0) {
            sqlExampleRenderingService.evictCache();
        }
        return toAjax(result);
    }

    /**
     * 修改 SQL 示例
     * <p>修改后清除渲染缓存</p>
     */
    @SaCheckPermission("ai:sqlExample:edit")
    @PutMapping
    public R<Void> edit(@Validated @RequestBody AiSqlExampleBo bo) {
        int result = sqlExampleService.updateByBo(bo);
        if (result > 0) {
            sqlExampleRenderingService.evictCache();
        }
        return toAjax(result);
    }

    /**
     * 删除 SQL 示例
     * <p>删除后清除全部渲染缓存</p>
     */
    @SaCheckPermission("ai:sqlExample:remove")
    @DeleteMapping("/{ids}")
    public R<Void> remove(@PathVariable Long[] ids) {
        int result = sqlExampleService.deleteByIds(ids);
        if (result > 0) {
            sqlExampleRenderingService.evictCache();
        }
        return toAjax(result);
    }
}

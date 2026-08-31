package com.nexus.online.controller;

import com.nexus.common.core.domain.R;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.generator.domain.GenTableDesign;
import com.nexus.online.domain.dto.TableDesignSaveDTO;
import com.nexus.online.service.ITableDesignService;
import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.annotation.Resource;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 在线表单设计 Controller
 * <p>提供在线表单设计的保存、列表查询、详情、删除、SQL 生成、物理建表与导入代码生成器等 REST 接口，
 * 属于在线低代码表单构建器模块（ruoyi-online）。</p>
 *
 * @author wanghuan
 */
@RestController
@RequestMapping("/online/db/design")
public class TableDesignController {

    @Resource
    /** 在线表设计业务服务，提供设计保存、SQL 生成、建表与导入代码生成等能力。 */
    private ITableDesignService tableDesignService;

    /**
     * 保存或更新表设计（将前端设计 JSON 持久化到 gen_table_design）。
     *
     * @param body 表设计入参，含设计 ID、名称、表名、表注释与内容
     * @return 保存后的设计 ID
     */
    @PostMapping("/save")
    public R<Long> save(@RequestBody TableDesignSaveDTO body) {
        GenTableDesign entity = new GenTableDesign();
        entity.setDesignId(body.getId());
        entity.setDesignName(body.getDesignName());
        entity.setTableName(body.getTableName());
        entity.setTableComment(body.getTableComment());
        entity.setContent(body.getContent());
        Long id = tableDesignService.saveOrUpdate(entity);
        return R.ok(id);
    }

    /**
     * 按关键字分页查询表设计列表（匹配设计名、表名或表注释）。
     *
     * @param keyword 可选查询关键字，为空时返回全部
     * @return 表设计列表，按更新时间倒序
     */
    @GetMapping("/list")
    public R<List<GenTableDesign>> list(@RequestParam(value = "keyword", required = false) String keyword) {
        return R.ok(tableDesignService.list(keyword));
    }

    /**
     * 查询指定表设计的详情。
     *
     * @param id 表设计 ID
     * @return 表设计实体
     */
    @GetMapping("/{id}")
    public R<GenTableDesign> detail(@PathVariable("id") Long id) {
        return R.ok(tableDesignService.getById(id));
    }

    /**
     * 删除指定表设计。
     *
     * @param id 待删除的表设计 ID
     * @return 操作结果，成功时返回成功状态
     */
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable("id") Long id) {
        tableDesignService.removeById(id);
        return R.ok();
    }

    /**
     * 根据设计 JSON 生成建表及外键 SQL（不执行）。
     *
     * @param body 请求体，含 design 的 content 字段（设计 JSON）
     * @return 包含生成完整 SQL 的响应（键 fullSql）
     */
    @PostMapping("/generate-sql")
    public R<Map<String, String>> generateSql(@RequestBody Map<String, Object> body) {
        String content = (String) body.get("content");
        String sql = tableDesignService.generateSql(content);
        Map<String, String> resp = new HashMap<>();
        resp.put("fullSql", sql);

        return R.ok(resp);
    }

    /**
     * 执行建表 SQL，在数据库中创建物理表。
     *
     * @param body 请求体，含 sql 与可选的 overwriteIfExists（存在时是否覆盖）
     * @return 操作结果，成功时返回成功状态
     */
    @SaCheckPermission("online:design:create")
    @PostMapping("/create-table")
    public R<Void> createTable(@RequestBody Map<String, Object> body) {
        String sql = (String) body.get("sql");
        boolean overwriteIfExists = Boolean.TRUE.equals(body.get("overwriteIfExists"));
        tableDesignService.createTables(sql, overwriteIfExists);
        return R.ok();
    }

    /**
     * 先创建物理表（按需覆盖）再将表设计导入代码生成器，便于后续生成业务代码。
     *
     * @param body 请求体，含 content、dataName（数据源名，默认 master）与 overwriteIfExists
     * @return 操作结果，成功时返回成功状态
     */
    @SaCheckPermission("online:design:create")
    @PostMapping("/import-to-gen")
    public R<Void> importToGen(@RequestBody Map<String, Object> body) {
        String content = (String) body.get("content");
        String dataName = (String) body.getOrDefault("dataName", "master");
        boolean overwriteIfExists = Boolean.TRUE.equals(body.get("overwriteIfExists"));
        // 先创建物理表（按需覆盖），再导入到代码生成
        String fullSql = tableDesignService.generateSql(content);
        tableDesignService.createTables(fullSql, overwriteIfExists);
        tableDesignService.importToGen(content, StringUtils.defaultIfBlank(dataName, "master"), overwriteIfExists);
        return R.ok();
    }
}

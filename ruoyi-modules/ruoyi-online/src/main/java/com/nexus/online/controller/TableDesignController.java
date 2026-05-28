package com.nexus.online.controller;

import com.nexus.common.core.domain.R;
import com.nexus.generator.domain.GenTableDesign;
import com.nexus.online.domain.dto.TableDesignSaveDTO;
import com.nexus.online.service.ITableDesignService;
import jakarta.annotation.Resource;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 在线表单设计Controller
 *
 * @author wanghuan
 */
@RestController
@RequestMapping("/online/db/design")
public class TableDesignController {

    @Resource
    private ITableDesignService tableDesignService;

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

    @GetMapping("/list")
    public R<List<GenTableDesign>> list(@RequestParam(value = "keyword", required = false) String keyword) {
        return R.ok(tableDesignService.list(keyword));
    }

    @GetMapping("/{id}")
    public R<GenTableDesign> detail(@PathVariable("id") Long id) {
        return R.ok(tableDesignService.getById(id));
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable("id") Long id) {
        tableDesignService.removeById(id);
        return R.ok();
    }

    @PostMapping("/generate-sql")
    public R<Map<String, String>> generateSql(@RequestBody Map<String, Object> body) {
        String content = (String) body.get("content");
        String sql = tableDesignService.generateSql(content);
        Map<String, String> resp = new HashMap<>();
        resp.put("fullSql", sql);

        return R.ok(resp);
    }

    @PostMapping("/create-table")
    public R<Void> createTable(@RequestBody Map<String, Object> body) {
        String sql = (String) body.get("sql");
        boolean overwriteIfExists = Boolean.TRUE.equals(body.get("overwriteIfExists"));
        tableDesignService.createTables(sql, overwriteIfExists);
        return R.ok();
    }

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

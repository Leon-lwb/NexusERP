package com.nexus.online.controller;

import com.nexus.common.core.controller.BaseController;
import com.nexus.common.core.domain.AjaxResult;
import com.nexus.online.domain.GenTableDesign;
import com.nexus.online.domain.dto.TableDesignSaveDTO;
import com.nexus.online.service.ITableDesignService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/online/db/design")
public class TableDesignController extends BaseController {

    @Autowired
    private ITableDesignService tableDesignService;

    @PreAuthorize("@ss.hasPermi('workflow:nform:edit')")
    @PostMapping("/save")
    public AjaxResult save(@RequestBody TableDesignSaveDTO body) {
        GenTableDesign entity = new GenTableDesign();
        entity.setDesignId(body.getId());
        entity.setDesignName(body.getDesignName());
        entity.setTableName(body.getTableName());
        entity.setTableComment(body.getTableComment());
        entity.setContent(body.getContent());
        Long id = tableDesignService.saveOrUpdate(entity);
        return success(id);
    }

    @PreAuthorize("@ss.hasPermi('workflow:nform:list')")
    @GetMapping("/list")
    public AjaxResult list(@RequestParam(value = "keyword", required = false) String keyword) {
        List<GenTableDesign> list = tableDesignService.list(keyword);
        return success(list);
    }

    @PreAuthorize("@ss.hasPermi('workflow:nform:list')")
    @GetMapping("/{id}")
    public AjaxResult detail(@PathVariable("id") Long id) {
        GenTableDesign design = tableDesignService.getById(id);
        return success(design);
    }

    @PreAuthorize("@ss.hasPermi('workflow:nform:remove')")
    @DeleteMapping("/{id}")
    public AjaxResult delete(@PathVariable("id") Long id) {
        tableDesignService.removeById(id);
        return success();
    }

    @PreAuthorize("@ss.hasPermi('workflow:nform:edit')")
    @PostMapping("/generate-sql")
    public AjaxResult generateSql(@RequestBody Map<String, Object> body) {
        String content = (String) body.get("content");
        String sql = tableDesignService.generateSql(content);
        Map<String, String> resp = new HashMap<>();
        resp.put("fullSql", sql);
        return success(resp);
    }

    @PreAuthorize("@ss.hasPermi('workflow:nform:edit')")
    @PostMapping("/create-table")
    public AjaxResult createTable(@RequestBody Map<String, Object> body) {
        String sql = (String) body.get("sql");
        boolean overwriteIfExists = Boolean.TRUE.equals(body.get("overwriteIfExists"));
        tableDesignService.createTables(sql, overwriteIfExists);
        return success();
    }

    @PreAuthorize("@ss.hasPermi('workflow:nform:edit')")
    @PostMapping("/import-to-gen")
    public AjaxResult importToGen(@RequestBody Map<String, Object> body) {
        String content = (String) body.get("content");
        String dataName = (String) body.getOrDefault("dataName", "master");
        boolean overwriteIfExists = Boolean.TRUE.equals(body.get("overwriteIfExists"));
        String fullSql = tableDesignService.generateSql(content);
        tableDesignService.createTables(fullSql, overwriteIfExists);
        tableDesignService.importToGen(content, dataName != null ? dataName : "master", overwriteIfExists);
        return success();
    }
}

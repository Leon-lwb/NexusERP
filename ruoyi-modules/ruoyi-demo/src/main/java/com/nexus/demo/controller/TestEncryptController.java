package com.nexus.demo.controller;

import com.nexus.common.core.domain.R;
import com.nexus.demo.domain.TestDemoEncrypt;
import com.nexus.demo.mapper.TestDemoEncryptMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;


/**
 * 测试数据库加解密功能
 *
 * @author wanghuan
 */
@Validated
@RestController
@RequestMapping("/demo/encrypt")
public class TestEncryptController {

    /** 测试加解密表 Mapper，用于插入与查询加密后的数据 */
    @Autowired
    private TestDemoEncryptMapper mapper;
    /** 是否开启数据库加解密（取自配置 mybatis-encryptor.enable） */
    @Value("${mybatis-encryptor.enable}")
    private Boolean encryptEnable;

    /**
     * 测试数据库加解密
     *
     * @param key   测试key
     * @param value 测试value
     */
    @GetMapping()
    public R<Map<String, TestDemoEncrypt>> test(String key, String value) {
        if (!encryptEnable) {
            throw new RuntimeException("加密功能未开启!");
        }
        Map<String, TestDemoEncrypt> map = new HashMap<>(2);
        TestDemoEncrypt demo = new TestDemoEncrypt();
        demo.setTestKey(key);
        demo.setValue(value);
        mapper.insert(demo);
        map.put("加密", demo);
        TestDemoEncrypt testDemo = mapper.selectById(demo.getId());
        map.put("解密", testDemo);
        return R.ok(map);
    }


}

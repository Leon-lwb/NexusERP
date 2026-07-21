package com.nexus.system.domain.enums;

/**
 * 自动编码组成部分类型枚举。
 * <p>定义编码中每一段内容的来源方式，如传入字符、当前日期、固定字符、流水号等，属于系统管理模块。</p>
 */
public enum PartTypeEnum {

    /** 传入字符 */
    PART_TYPE_INPUTCHAR("INPUTCHAR", "传入字符", 0),
    /** 当前日期 */
    PART_TYPE_NOWDATE("NOWDATE", "当前日期", 1),
    /** 固定字符 */
    PART_TYPE_FIXCHAR("FIXCHAR", "固定字符", 2),
    /** 流水号 */
    PART_TYPE_SERIALNO("SERIALNO", "流水号", 3),
    /** 其他 */
    PART_TYPE_OTHER("OTHER", "其他", 99);

    /** 组成部分类型编码 */
    private final String code;
    /** 组成部分类型名称 */
    private final String name;
    /** 组成部分类型索引（用于策略匹配） */
    private final Integer beanIndex;

    PartTypeEnum(String code, String name, Integer beanIndex) {
        this.code = code;
        this.name = name;
        this.beanIndex = beanIndex;
    }

    /**
     * 获取组成部分类型编码。
     *
     * @return 编码
     */
    public String getCode() { return code; }

    /**
     * 获取组成部分类型名称。
     *
     * @return 名称
     */
    public String getName() { return name; }

    /**
     * 获取组成部分类型索引。
     *
     * @return 索引
     */
    public Integer getBeanIndex() { return beanIndex; }

    /**
     * 根据编码查找对应的组成部分类型枚举，未匹配时返回 {@code OTHER}。
     *
     * @param code 组成部分类型编码
     * @return 匹配的枚举值
     */
    public static PartTypeEnum getByCode(String code) {
        for (PartTypeEnum value : PartTypeEnum.values()) {
            if (value.getCode().equals(code)) {
                return value;
            }
        }
        return PART_TYPE_OTHER;
    }
}

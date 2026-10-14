package com.nexus.common.report.catalog;

import com.nexus.common.report.ValueType;

import java.util.*;

/**
 * 代码维护的固定实体目录；物理表名和列名不属于客户端查询协议。
 *
 * @param version 当前契约的version配置或状态
 * @param entities 当前契约的entities配置或状态
 * @param relations 当前契约的relations配置或状态
 */
public record ReportEntityCatalog(int version, List<Entity> entities, List<Relation> relations) {

    /**
     * 冻结目录集合并拒绝重复逻辑标识。
     *
     * @param version 当前契约的version配置或状态
     * @param entities 当前契约的entities配置或状态
     * @param relations 当前契约的relations配置或状态
     */
    public ReportEntityCatalog {
        entities = List.copyOf(entities);
        relations = List.copyOf(relations);
        if (version < 1
                || entities.isEmpty()
                || entities.stream().map(Entity::id).distinct().count() != entities.size()
                || relations.stream().map(Relation::id).distinct().count() != relations.size())
            throw new IllegalArgumentException("实体目录标识重复或版本无效");
        for (var entity : entities) {
            identifier(entity.id());
            identifier(entity.table());
            identifier(entity.key());
            identifier(entity.tenantColumn());
            if (entity.permission() == null || entity.permission().isBlank())
                throw new IllegalArgumentException("实体权限未声明");
            if (entity.fields().stream().map(Field::id).distinct().count()
                    != entity.fields().size()) throw new IllegalArgumentException("实体字段重复");
            for (var field : entity.fields()) {
                if (field.id() == null
                        || !field.id().matches("[A-Za-z_][A-Za-z0-9_]{0,63}")
                        || Set.of("__proto__", "constructor", "prototype").contains(field.id()))
                    throw new IllegalArgumentException("逻辑字段标识无效");
                identifier(field.column());
            }
        }
        for (var relation : relations) {
            if (entities.stream().noneMatch(e -> e.id().equals(relation.from()))
                    || entities.stream().noneMatch(e -> e.id().equals(relation.to())))
                throw new IllegalArgumentException("关系实体不存在");
            identifier(relation.fromColumn());
            identifier(relation.toColumn());
            if (!Set.of("ONE_TO_ONE", "MANY_TO_ONE", "ONE_TO_MANY").contains(relation.cardinality())
                    || !Set.of("LEFT", "INNER").contains(relation.joinType()))
                throw new IllegalArgumentException("关系基数或连接类型无效");
        }
    }

    /**
     * 按逻辑实体标识读取固定目录。
     *
     * @param id 固定标识，按字符串传输以保留完整精度
     * @return 当前受控处理的固定结果
     */
    public Entity entity(String id) {
        return entities.stream()
                .filter(e -> e.id().equals(id))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("实体未在目录登记"));
    }

    /**
     * 按代码批准的关系标识读取连接定义。
     *
     * @param id 固定标识，按字符串传输以保留完整精度
     * @return 当前受控处理的固定结果
     */
    public Relation relation(String id) {
        return relations.stream()
                .filter(r -> r.id().equals(id))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("关系未在目录登记"));
    }

    /**
     * 校验代码侧物理标识，防止错误配置成为查询片段。
     *
     * @param value 当前契约的value配置或状态
     */
    private static void identifier(String value) {
        if (value == null || !value.matches("[a-z][a-z0-9_]{0,63}"))
            throw new IllegalArgumentException("目录标识无效");
    }

    /**
     * 可信实体及自身行范围；明细可通过父实体进一步检查所属单据。
     *
     * @param id 固定标识，按字符串传输以保留完整精度
     * @param label 用户可见标签
     * @param table 当前契约的table配置或状态
     * @param key 当前契约的key配置或状态
     * @param permission 当前契约的permission配置或状态
     * @param tenantColumn 当前契约的tenantColumn配置或状态
     * @param deleteColumn 当前契约的deleteColumn配置或状态
     * @param departmentColumn 当前契约的departmentColumn配置或状态
     * @param ownerColumn 当前契约的ownerColumn配置或状态
     * @param parent 当前契约的parent配置或状态
     * @param fields 当前契约的fields配置或状态
     */
    public record Entity(
            String id,
            String label,
            String table,
            String key,
            String permission,
            String tenantColumn,
            String deleteColumn,
            String departmentColumn,
            String ownerColumn,
            ParentPolicy parent,
            List<Field> fields) {
        /**
         * 固定字段并校验策略列名。
         *
         * @param id 固定标识，按字符串传输以保留完整精度
         * @param label 用户可见标签
         * @param table 当前契约的table配置或状态
         * @param key 当前契约的key配置或状态
         * @param permission 当前契约的permission配置或状态
         * @param tenantColumn 当前契约的tenantColumn配置或状态
         * @param deleteColumn 当前契约的deleteColumn配置或状态
         * @param departmentColumn 当前契约的departmentColumn配置或状态
         * @param ownerColumn 当前契约的ownerColumn配置或状态
         * @param parent 当前契约的parent配置或状态
         * @param fields 当前契约的fields配置或状态
         */
        public Entity {
            fields = List.copyOf(fields);
            for (var column :
                    List.of(
                            deleteColumn == null ? "" : deleteColumn,
                            departmentColumn == null ? "" : departmentColumn,
                            ownerColumn == null ? "" : ownerColumn))
                if (!column.isEmpty()) identifier(column);
        }

        /**
         * 读取实体内的逻辑字段，不接受物理列名。
         *
         * @param id 固定标识，按字符串传输以保留完整精度
         * @return 当前受控处理的固定结果
         */
        public Field field(String id) {
            return fields.stream()
                    .filter(f -> f.id().equals(id))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("字段未在目录登记"));
        }
    }

    /**
     * 稳定字段类型、字段权限及数值汇总必须保留的口径维度。
     *
     * @param id 固定标识，按字符串传输以保留完整精度
     * @param label 用户可见标签
     * @param column 当前契约的column配置或状态
     * @param type 受控类型
     * @param permission 当前契约的permission配置或状态
     * @param aggregationKeys 当前契约的aggregationKeys配置或状态
     * @param aggregatable 当前契约的aggregatable配置或状态
     */
    public record Field(
            String id,
            String label,
            String column,
            ValueType type,
            String permission,
            List<String> aggregationKeys,
            boolean aggregatable) {
        /**
         * 初始化当前契约或受控处理依赖。
         *
         * @param id 当前受控操作的id输入
         * @param label 当前受控操作的label输入
         * @param column 当前受控操作的column输入
         * @param type 当前受控操作的type输入
         * @param permission 当前受控操作的permission输入
         * @param aggregationKeys 当前受控操作的aggregationKeys输入
         * @param aggregatable 当前受控操作的aggregatable输入
         */
        public Field {
            Objects.requireNonNull(type);
            aggregationKeys = List.copyOf(aggregationKeys);
        }
    }

    /**
     * 代码批准的实体关系，限制连接方向、类型和粒度。
     *
     * @param id 固定标识，按字符串传输以保留完整精度
     * @param from 当前契约的from配置或状态
     * @param to 当前契约的to配置或状态
     * @param fromColumn 当前契约的fromColumn配置或状态
     * @param toColumn 当前契约的toColumn配置或状态
     * @param cardinality 当前契约的cardinality配置或状态
     * @param joinType 当前契约的joinType配置或状态
     */
    public record Relation(
            String id,
            String from,
            String to,
            String fromColumn,
            String toColumn,
            String cardinality,
            String joinType) {}

    /**
     * 明细通过父单据所属范围校验，不推断明细表自带组织列。
     *
     * @param entity 当前契约的entity配置或状态
     * @param childColumn 当前契约的childColumn配置或状态
     * @param parentColumn 当前契约的parentColumn配置或状态
     */
    public record ParentPolicy(String entity, String childColumn, String parentColumn) {}
}

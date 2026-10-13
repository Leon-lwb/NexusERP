package com.nexus.common.report;
import java.util.*;
public record DatasetDescriptor(String code, String name, int version, String permission, boolean document,
    List<DatasetField> fields, List<DatasetParameter> parameters, List<String> defaultColumns) {
    public DatasetDescriptor { fields = List.copyOf(fields); parameters = List.copyOf(parameters); defaultColumns = List.copyOf(defaultColumns); }
    public DatasetField field(String name) { return fields.stream().filter(field -> field.name().equals(name)).findFirst().orElseThrow(() -> new IllegalArgumentException("Unknown dataset field: " + name)); }
}

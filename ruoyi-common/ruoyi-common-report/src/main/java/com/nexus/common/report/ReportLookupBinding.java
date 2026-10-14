package com.nexus.common.report;

/** Only code-owned dataset contract/parameter combinations can expose a lookup. */
public record ReportLookupBinding(
        String datasetCode,
        int providerVersion,
        String parameter,
        String label,
        String permission) {}

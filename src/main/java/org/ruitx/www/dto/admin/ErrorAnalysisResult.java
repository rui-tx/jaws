package org.ruitx.www.dto.admin;

import java.util.List;
import java.util.Map;

/**
 * Response DTO for error analysis results
 */
public record ErrorAnalysisResult(
    String jobType,
    int analysisHours,
    Map<String, Integer> errorTypeCount,
    Map<String, Double> errorTrends,
    List<String> recommendations,
    long timestamp
) {} 
package com.example.kafka_es.kafka;

public final class Topics {
    private Topics() {}

    // Raw input comments from YouTube (producer -> AI inference)
    public static final String RAW_COMMENTS      = "RawComments";

    // AI inference results (sentence-level categorized outputs)
    public static final String ANALYZED_COMMENTS = "analyzed_comments";

    // Control plane: START(expected_count) / END per video_id
    public static final String ANALYSIS_CONTROL  = "analysis_control";

    // Completion signal emitted after END & processed>=expected
    public static final String ANALYSIS_DONE     = "analysis_done";

}

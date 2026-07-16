package com.example.podclassic.media

enum class TubeAmpPreset(val displayName: String, val description: String) {
    NONE("关闭", "不处理原始 PCM"),
    WARM("暖润", "轻微偶次谐波，适合人声与原声音乐"),
    SMOOTH("醇厚", "温和饱和与慢速动态，适合流行音乐"),
    VINTAGE("复古", "更明显的胆味与高频柔化"),
    DYNAMIC("动态", "更快响应，保留摇滚和电子音乐瞬态")
}

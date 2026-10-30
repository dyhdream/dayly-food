package com.daylyfood.app.data.ai

/**
 * A provider preset. All three speak the OpenAI-compatible chat completions shape,
 * so one client covers them; only the URL and model names differ.
 */
data class AiProvider(
    val id: String,
    val label: String,
    val hint: String,
    val chatUrl: String,
    val textModel: String,
    /** Empty when the provider has no vision model, which disables photo recognition. */
    val visionModel: String,
    val freeNote: String?,
) {
    val supportsVision: Boolean get() = visionModel.isNotEmpty()
}

object AiProviders {

    val ZHIPU = AiProvider(
        id = "zhipu",
        label = "智谱 GLM",
        hint = "手机号注册即可，识图模型有免费档",
        chatUrl = "https://open.bigmodel.cn/api/paas/v4/chat/completions",
        textModel = "glm-4-flash",
        visionModel = "glm-4v-flash",
        freeNote = "GLM-4V-Flash 免费",
    )

    val QWEN = AiProvider(
        id = "qwen",
        label = "阿里通义",
        hint = "阿里云百炼申请 Key，按量计费",
        chatUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions",
        textModel = "qwen-plus",
        visionModel = "qwen-vl-plus",
        freeNote = "新用户有免费额度",
    )

    val DEEPSEEK = AiProvider(
        id = "deepseek",
        label = "DeepSeek",
        hint = "便宜，但只有文本，不能识图",
        chatUrl = "https://api.deepseek.com/chat/completions",
        textModel = "deepseek-chat",
        visionModel = "",
        freeNote = null,
    )

    val ALL = listOf(ZHIPU, QWEN, DEEPSEEK)

    val DEFAULT = ZHIPU

    fun byId(id: String): AiProvider = ALL.firstOrNull { it.id == id } ?: DEFAULT
}

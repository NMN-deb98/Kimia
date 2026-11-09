package ir.kimia.app.data

/**
 * A downloadable GGUF model. All entries are official upstream files hosted on Hugging Face.
 * [minRamMb] is compared with the RAM Android reports (which is a bit below the marketing number:
 * a "4 GB" phone usually reports ~3.6 GB).
 */
data class ModelInfo(
    val id: String,
    val name: String,
    val description: String,
    val url: String,
    val fileName: String,
    val approxSizeMb: Int,
    val minRamMb: Long,
    val license: String,
)

object ModelCatalog {
    val models = listOf(
        ModelInfo(
            id = "qwen2.5-0.5b",
            name = "Qwen2.5 0.5B (خیلی سبک)",
            description = "برای گوشی‌های ضعیف. سریع است ولی فارسی‌اش ضعیف و پاسخ‌هایش ساده است.",
            url = "https://huggingface.co/Qwen/Qwen2.5-0.5B-Instruct-GGUF/resolve/main/qwen2.5-0.5b-instruct-q4_k_m.gguf",
            fileName = "qwen2.5-0.5b-instruct-q4_k_m.gguf",
            approxSizeMb = 400,
            minRamMb = 1700,
            license = "Apache-2.0",
        ),
        ModelInfo(
            id = "qwen2.5-1.5b",
            name = "Qwen2.5 1.5B (متعادل)",
            description = "تعادل خوب سرعت و کیفیت. فارسی قابل‌قبول و مناسب گوشی‌های ۴ گیگ.",
            url = "https://huggingface.co/Qwen/Qwen2.5-1.5B-Instruct-GGUF/resolve/main/qwen2.5-1.5b-instruct-q4_k_m.gguf",
            fileName = "qwen2.5-1.5b-instruct-q4_k_m.gguf",
            approxSizeMb = 1120,
            minRamMb = 3400,
            license = "Apache-2.0",
        ),
        ModelInfo(
            id = "qwen2.5-3b",
            name = "Qwen2.5 3B (باکیفیت‌تر)",
            description = "بهترین کیفیت این فهرست، برای گوشی‌های ۶ گیگ به بالا. کندتر است.",
            url = "https://huggingface.co/Qwen/Qwen2.5-3B-Instruct-GGUF/resolve/main/qwen2.5-3b-instruct-q4_k_m.gguf",
            fileName = "qwen2.5-3b-instruct-q4_k_m.gguf",
            approxSizeMb = 2100,
            minRamMb = 5200,
            license = "Qwen Research (فقط غیرتجاری)",
        ),
    )

    /** Largest model this phone's RAM can reasonably hold, or null if even the smallest is too big. */
    fun recommendedFor(device: DeviceInfo): ModelInfo? =
        models.filter { it.minRamMb <= device.totalRamMb }.maxByOrNull { it.minRamMb }

    fun byFileName(name: String): ModelInfo? = models.firstOrNull { it.fileName == name }
}

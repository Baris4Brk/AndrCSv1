package com.tom.rv2ide.handlers

import android.view.View
import androidx.lifecycle.LifecycleCoroutineScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.google.android.material.textview.MaterialTextView
import androidx.recyclerview.widget.RecyclerView
import android.widget.LinearLayout
import com.tom.rv2ide.adapters.AgentTimelineAdapter
import com.tom.rv2ide.adapters.FileModificationAdapter
import com.tom.rv2ide.artificial.agents.AIAgentManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class AIRequestHandler(
    private val lifecycleScope: LifecycleCoroutineScope,
    private val aiAgent: AIAgentManager,
    private val statusText: MaterialTextView,
    private val summaryText: MaterialTextView,
    private val progressIndicator: CircularProgressIndicator,
    private val executeBtn: MaterialButton,
    private val fileModificationList: RecyclerView,
    private val fileModificationAdapter: FileModificationAdapter,
    private val agentTimelineList: RecyclerView,
    private val agentTimelineAdapter: AgentTimelineAdapter,
    private val summaryCard: LinearLayout,
    private val onFileOpen: (String) -> Unit,
    private val onTypeText: (String, Long) -> Unit,
    private val getCurrentFile: () -> File?,
    private val refreshEditor: () -> Unit
) {
    
    private var executionJob: Job? = null
    
    fun execute(userRequest: String) {
        executionJob?.cancel()
        executionJob = lifecycleScope.launch {
            try {
                withContext(Dispatchers.Main) {
                    executeBtn.isEnabled = false
                    progressIndicator.visibility = View.VISIBLE
                    summaryCard.visibility = View.GONE
                    fileModificationAdapter.clear()
                    fileModificationList.visibility = View.GONE
                    agentTimelineAdapter.clear()
                    appendTimeline(
                        AgentTimelineAdapter.Kind.USER,
                        "İstek",
                        userRequest
                    )
                }
                
                executeAIRequest(userRequest)
                
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    executeBtn.isEnabled = true
                    progressIndicator.visibility = View.GONE
                    statusText.text = "❌ Hata: ${translateAiText(e.message ?: "Bilinmeyen hata")}" 
                }
            }
        }
    }
    
    private suspend fun executeAIRequest(userRequest: String) {
        aiAgent.executeRequest(userRequest, object : AIAgentManager.AIAgentCallback {
            override fun onProcessing(message: String) {
                lifecycleScope.launch(Dispatchers.Main) {
                    val translated = translateAiText(message)
                    statusText.text = translated
                    appendTimeline(AgentTimelineAdapter.Kind.PLAN, "Ajan", translated)
                }
            }

            override fun onFileModifying(filePath: String, fileName: String) {
                lifecycleScope.launch(Dispatchers.Main) {
                    if (fileModificationList.visibility == View.GONE) {
                        fileModificationList.visibility = View.VISIBLE
                    }
                    fileModificationAdapter.addItem(fileName)
                    appendTimeline(
                        AgentTimelineAdapter.Kind.TOOL,
                        "Dosya yazma",
                        "$fileName dosyası onaylanıp uygulanmak üzere hazırlanıyor"
                    )
                }
            }

            override fun onFileModified(filePath: String, fileName: String, success: Boolean) {
                lifecycleScope.launch(Dispatchers.Main) {
                    fileModificationAdapter.updateItemStatus(fileName, success)
                    
                    if (getCurrentFile()?.name == fileName && success) {
                        refreshEditor()
                    }
                    appendTimeline(
                        if (success) AgentTimelineAdapter.Kind.RESULT else AgentTimelineAdapter.Kind.ERROR,
                        if (success) "İşlem tamamlandı" else "İşlem başarısız",
                        if (success) "$fileName güncellendi" else "$fileName güncellenemedi"
                    )
                }
            }

            override fun onSuccess(
                response: String,
                modifications: List<AIAgentManager.ModificationResult>,
                summary: AIAgentManager.ModificationSummary
            ) {
                lifecycleScope.launch(Dispatchers.Main) {
                    handleSuccess(response, modifications, summary)
                }
            }

            override fun onTextResponse(
                response: String,
                summary: AIAgentManager.ModificationSummary
            ) {
                lifecycleScope.launch(Dispatchers.Main) {
                    handleTextResponse(response)
                }
            }

            override fun onError(message: String) {
                lifecycleScope.launch(Dispatchers.Main) {
                    handleError(message)
                }
            }

            override fun onRetry(attemptNumber: Int, message: String) {
                lifecycleScope.launch(Dispatchers.Main) {
                    statusText.text = "🔄 Yeniden deneniyor #$attemptNumber: ${translateAiText(message)}"
                }
            }
        })
    }
    
    private fun handleSuccess(
        response: String,
        modifications: List<AIAgentManager.ModificationResult>,
        summary: AIAgentManager.ModificationSummary
    ) {
        progressIndicator.visibility = View.GONE
        statusText.text = "✅ İşlem tamamlandı"
        appendTimeline(
            AgentTimelineAdapter.Kind.RESULT,
            "İstek tamamlandı",
            "${summary.successfulFiles} dosya güncellendi"
        )
        summaryText.text = buildSummaryText(summary)
        summaryCard.visibility = View.VISIBLE
        
        if (modifications.isNotEmpty()) {
            val firstMod = modifications.first()
            val file = File(firstMod.filePath)
            if (file.exists()) {
                onFileOpen(file.name)
            }
        }
        
        executeBtn.isEnabled = true
    }

    private fun handleTextResponse(response: String) {
        progressIndicator.visibility = View.GONE
        executeBtn.isEnabled = true
        statusText.text = response
        appendTimeline(AgentTimelineAdapter.Kind.RESULT, "Yanıt", response)
        summaryCard.visibility = View.GONE
        fileModificationList.visibility = View.GONE
    }
    
    private fun handleError(message: String) {
        progressIndicator.visibility = View.GONE
        executeBtn.isEnabled = true
        
        val translated = translateAiText(message)
        statusText.text = """
❌ HATA OLUŞTU

$translated

Hata mesajını kontrol edip tekrar deneyin.
        """.trimIndent()
        appendTimeline(AgentTimelineAdapter.Kind.ERROR, "Ajan hatası", translated)
    }
    
    private fun buildSummaryText(summary: AIAgentManager.ModificationSummary): String {
        val builder = StringBuilder()
        builder.append("📊 Toplam dosya: ${summary.totalFiles}\n")
        builder.append("✅ Başarılı: ${summary.successfulFiles}\n")
        if (summary.failedFiles > 0) {
            builder.append("❌ Başarısız: ${summary.failedFiles}\n")
        }
        builder.append("🆕 Yeni dosya: ${summary.newFiles}\n")
        builder.append("✏️ Değiştirilen dosya: ${summary.modifiedFiles}\n\n")
        
        builder.append("Dosyalar:\n")
        summary.fileDetails.forEach { detail ->
            val icon = if (detail.status == AIAgentManager.FileStatus.SUCCESS) "✅" else "❌"
            val type = if (detail.changeType == AIAgentManager.ChangeType.CREATED) "Oluşturuldu" else "Değiştirildi"
            builder.append("$icon $type: ${detail.fileName}\n")
        }
        
        return builder.toString()
    }
    
    /**
     * AI tarafındaki kullanıcıya görünen İngilizce durum ve hata metinlerini
     * burada Türkçeleştirir. Sağlayıcı adları, model adları ve API hata detayları
     * aynen korunur; yalnızca arayüz metinleri çevrilir.
     */
    private fun translateAiText(text: String): String {
        return text
            .replace("Analyzing your request...", "İsteğiniz analiz ediliyor...")
            .replace("Thinking differently...", "Farklı bir şekilde düşünüyor...")
            .replace("Modifying files...", "Dosyalar değiştiriliyor...")
            .replace("Some files failed. Retrying...", "Bazı dosyalar değiştirilemedi. Yeniden deneniyor...")
            .replace("No files were modified. Retrying...", "Hiçbir dosya değiştirilemedi. Yeniden deneniyor...")
            .replace("Failed after ", "Şu kadar denemeden sonra başarısız oldu: ")
            .replace(" attempts with ", " deneme. Ajan: ")
            .replace("No agent initialized", "Ajan başlatılmadı")
            .replace("Please check your API key and try again.", "Lütfen API anahtarınızı kontrol edip tekrar deneyin.")
            .replace("Error: ", "Hata: ")
            .replace(". Retrying...", ". Yeniden deneniyor...")
            .replace("Exception: ", "İstisna: ")
            .replace(". Trying again...", ". Tekrar deneniyor...")
            .replace("Auto-switching to another provider...", "Başka bir sağlayıcıya otomatik geçiliyor...")
            .replace("Switched to ", "Şuna geçildi: ")
            .replace("Failed to switch providers.", "Sağlayıcı değiştirilemedi.")
            .replace("No alternative providers available.", "Kullanılabilir alternatif sağlayıcı yok.")
            .replace("Modified successfully", "Başarıyla değiştirildi")
            .replace("RATE LIMIT EXCEEDED", "İSTEK SINIRI AŞILDI")
            .replace("The API rate limit has been exceeded.", "API istek sınırı aşıldı.")
            .replace("Please wait a few minutes before trying again.", "Lütfen tekrar denemeden önce birkaç dakika bekleyin.")
            .replace("QUOTA EXCEEDED", "KOTA AŞILDI")
            .replace("Your API quota has been exhausted.", "API kotanız tükendi.")
            .replace("Please check your billing or upgrade your plan.", "Lütfen faturalandırma bilgilerinizi kontrol edin veya planınızı yükseltin.")
            .replace("INSUFFICIENT BALANCE", "YETERSİZ BAKİYE")
            .replace("Your account balance is too low to process this request.", "Hesap bakiyeniz bu isteği işlemek için yetersiz.")
            .replace("Please add credits or upgrade your plan.", "Lütfen kredi ekleyin veya planınızı yükseltin.")
            .replace("INVALID API KEY", "GEÇERSİZ API ANAHTARI")
            .replace("The API key is invalid or expired.", "API anahtarı geçersiz veya süresi dolmuş.")
            .replace("Please update your API key in the configuration.", "Lütfen yapılandırmadaki API anahtarınızı güncelleyin.")
            .replace("NETWORK ERROR", "AĞ HATASI")
            .replace("Could not connect to the API server.", "API sunucusuna bağlanılamadı.")
            .replace("Please check your internet connection.", "Lütfen internet bağlantınızı kontrol edin.")
            .replace("TIMEOUT ERROR", "ZAMAN AŞIMI HATASI")
            .replace("The request took too long to complete.", "İsteğin tamamlanması çok uzun sürdü.")
            .replace("JSON PARSING ERROR", "JSON AYRIŞTIRMA HATASI")
            .replace("Failed to parse API response.", "API yanıtı ayrıştırılamadı.")
            .replace("The API may be experiencing issues.", "API tarafında bir sorun olabilir.")
            .replace("ERROR OCCURRED", "HATA OLUŞTU")
            .replace("Error Type:", "Hata türü:")
            .replace("Message:", "Mesaj:")
            .replace("Provider:", "Sağlayıcı:")
            .replace("Details:", "Ayrıntılar:")
            .replace("Stack Trace (first 500 chars):", "Yığın izi (ilk 500 karakter):")
            // Sağlayıcıların kendi servis/hata metinleri de kullanıcıya ulaşabildiği için
            // burada ayrıca Türkçeleştiriyoruz.
            .replace("Gemini AI service not initialized", "Gemini yapay zekâ hizmeti başlatılmadı")
            .replace("Gemini API quota exceeded. Switching to another provider...", "Gemini API kotası aşıldı. Başka bir sağlayıcıya geçiliyor...")
            .replace("Gemini rate limit exceeded. Switching to another provider...", "Gemini istek sınırı aşıldı. Başka bir sağlayıcıya geçiliyor...")
            .replace("Invalid Gemini API key. Please check your configuration.", "Geçersiz Gemini API anahtarı. Lütfen yapılandırmanızı kontrol edin.")
            .replace("OpenAI service not initialized", "OpenAI hizmeti başlatılmadı")
            .replace("OpenAI rate limit exceeded: ", "OpenAI istek sınırı aşıldı: ")
            .replace("OpenAI quota exceeded: ", "OpenAI API kotası aşıldı: ")
            .replace("Invalid OpenAI API key: ", "Geçersiz OpenAI API anahtarı: ")
            .replace("OpenAI authentication failed: ", "OpenAI kimlik doğrulaması başarısız: ")
            .replace("OpenAI API error", "OpenAI API hatası")
            .replace("No response from OpenAI API", "OpenAI API'den yanıt alınamadı")
            .replace("OpenAI request timeout: ", "OpenAI isteğinde zaman aşımı: ")
            .replace("Network error - cannot reach OpenAI: ", "Ağ hatası - OpenAI'ye ulaşılamadı: ")
            .replace("File writer not initialized", "Dosya yazma bileşeni başlatılmadı")
            .replace("Empty response from AI", "Yapay zekâdan boş yanıt alındı")
    }

    fun cancel() {
        executionJob?.cancel()
    }

    private fun appendTimeline(
        kind: AgentTimelineAdapter.Kind,
        title: String,
        detail: String
    ) {
        agentTimelineAdapter.add(AgentTimelineAdapter.Event(kind, title, detail))
        agentTimelineList.scrollToPosition(agentTimelineAdapter.itemCount - 1)
    }
}

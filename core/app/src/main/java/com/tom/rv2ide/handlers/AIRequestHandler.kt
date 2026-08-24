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
                    statusText.text = "❌ Hata: ${e.message}"
                }
            }
        }
    }
    
    private suspend fun executeAIRequest(userRequest: String) {
        aiAgent.executeRequest(userRequest, object : AIAgentManager.AIAgentCallback {
            override fun onProcessing(message: String) {
                lifecycleScope.launch(Dispatchers.Main) {
                    statusText.text = message
                    appendTimeline(AgentTimelineAdapter.Kind.PLAN, "Ajan", message)
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
                    statusText.text = "🔄 Yeniden deneniyor #$attemptNumber: $message"
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
        
        statusText.text = """
❌ HATA OLUŞTU

$message

Hata mesajını kontrol edip tekrar deneyin.
        """.trimIndent()
        appendTimeline(AgentTimelineAdapter.Kind.ERROR, "Ajan hatası", message)
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
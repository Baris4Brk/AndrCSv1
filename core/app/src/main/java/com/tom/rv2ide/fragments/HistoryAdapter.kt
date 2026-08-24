package com.tom.rv2ide.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.tom.rv2ide.R
import com.tom.rv2ide.artificial.agents.UnifiedModificationAttempt
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HistoryAdapter : RecyclerView.Adapter<HistoryAdapter.HistoryViewHolder>() {

    private val items = mutableListOf<UnifiedModificationAttempt>()
    private val dateFormat = SimpleDateFormat("dd MMM yyyy HH:mm:ss", Locale.getDefault())

    fun setItems(newItems: List<UnifiedModificationAttempt>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HistoryViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_history, parent, false)
        return HistoryViewHolder(view)
    }

    override fun onBindViewHolder(holder: HistoryViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class HistoryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val historyIcon: ImageView = itemView.findViewById(R.id.historyIcon)
        private val historyTitle: TextView = itemView.findViewById(R.id.historyTitle)
        private val historyTime: TextView = itemView.findViewById(R.id.historyTime)
        private val historyDetails: TextView = itemView.findViewById(R.id.historyDetails)

        fun bind(item: UnifiedModificationAttempt) {
            val context = itemView.context
            val fileName = File(item.filePath).name
            val status = if (item.success) {
                context.getString(R.string.ai_history_modified)
            } else {
                context.getString(R.string.ai_history_failed_modify)
            }

            historyTitle.text = "$status $fileName"
            historyTime.text = dateFormat.format(Date(item.timestamp))
            historyDetails.text = context.getString(
                R.string.ai_history_attempt,
                item.attemptNumber
            ) + " - " + context.getString(
                if (item.success) R.string.ai_history_success else R.string.ai_history_failed
            )

            // Başarılı ve başarısız işlemleri mevcut ikon davranışını bozmadan ayırıyoruz.
            val colorRes = if (item.success) {
                android.R.color.holo_green_dark
            } else {
                android.R.color.holo_red_dark
            }
            historyIcon.setColorFilter(context.getColor(colorRes))
        }
    }
}

/*
 *  This file is part of AndroidCodeStudio.
 *
 *  AndroidCodeStudio is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  AndroidCodeStudio is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *   along with AndroidCodeStudio.  If not, see <https://www.gnu.org/licenses/>.
*/

package com.tom.rv2ide.artificial.dialogs

import android.content.Context
import android.content.SharedPreferences
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import android.preference.PreferenceManager

/*
 * @author Mohammed-baqer-null @ https://github.com/Mohammed-baqer-null
*/

class ProviderSwitchDialog(private val context: Context) {
    
    private val sp: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
    private val AUTO_SWITCH_KEY = "auto_switch_providers"
    
    fun isAutoSwitchEnabled(): Boolean {
        return sp.getBoolean(AUTO_SWITCH_KEY, false)
    }
    
    fun setAutoSwitch(enabled: Boolean) {
        sp.edit().putBoolean(AUTO_SWITCH_KEY, enabled).apply()
    }
    
    fun showProviderErrorDialog(
        currentProvider: String,
        errorMessage: String,
        availableProviders: List<Pair<String, String>>, // List of (id, name)
        onProviderSelected: (String) -> Unit,
        onEnableAutoSwitch: () -> Unit
    ) {
        if (availableProviders.isEmpty()) {
            showNoProvidersAvailableDialog(errorMessage)
            return
        }
        
        val providerNames = availableProviders.map { it.second }.toTypedArray()
        
        MaterialAlertDialogBuilder(context)
            .setTitle("⚠️ Sağlayıcı Hatası")
            .setMessage(
                "Mevcut sağlayıcı: $currentProvider\n\n" +
                "Hata: $errorMessage\n\n" +
                "Kullanılabilir sağlayıcı sayısı: ${availableProviders.size}\n\n" +
                "Başka bir sağlayıcıya geçmek ister misiniz?"
            )
            .setPositiveButton("Manuel Geçiş") { dialog, _ ->
                dialog.dismiss()
                showProviderSelectionDialog(availableProviders, onProviderSelected)
            }
            .setNegativeButton("Otomatik Geçişi Etkinleştir") { dialog, _ ->
                setAutoSwitch(true)
                onEnableAutoSwitch()
                dialog.dismiss()
            }
            .setNeutralButton("İptal") { dialog, _ ->
                dialog.dismiss()
            }
            .setCancelable(false)
            .show()
    }
    
    private fun showProviderSelectionDialog(
        availableProviders: List<Pair<String, String>>,
        onProviderSelected: (String) -> Unit
    ) {
        val providerNames = availableProviders.map { it.second }.toTypedArray()
        val providerIds = availableProviders.map { it.first }
        
        MaterialAlertDialogBuilder(context)
            .setTitle("Sağlayıcı Seç")
            .setItems(providerNames) { dialog, which ->
                onProviderSelected(providerIds[which])
                dialog.dismiss()
            }
            .setNegativeButton("İptal") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }
    
    private fun showNoProvidersAvailableDialog(errorMessage: String) {
        MaterialAlertDialogBuilder(context)
            .setTitle("❌ Kullanılabilir Sağlayıcı Yok")
            .setMessage(
                "Hata: $errorMessage\n\n" +
                "Geçerli API anahtarına sahip başka kullanılabilir sağlayıcı yok.\n\n" +
                "Lütfen:\n" +
                "1. API anahtarlarınızı kontrol edin\n" +
                "2. Hesap kotalarınızı kontrol edin\n" +
                "3. Daha sonra tekrar deneyin"
            )
            .setPositiveButton("Tamam") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }
    
    fun showAutoSwitchNotification(
        fromProvider: String,
        toProvider: String,
        reason: String
    ): MaterialAlertDialogBuilder {
        return MaterialAlertDialogBuilder(context)
            .setTitle("🔄 Sağlayıcı Otomatik Değiştirildi")
            .setMessage(
                "Önceki sağlayıcı: $fromProvider\n" +
                "Yeni sağlayıcı: $toProvider\n\n" +
                "Neden: $reason\n\n" +
                "Otomatik geçiş etkin. Bunu ayarlardan devre dışı bırakabilirsiniz."
            )
            .setPositiveButton("Tamam") { dialog, _ ->
                dialog.dismiss()
            }
            .setNegativeButton("Otomatik Geçişi Devre Dışı Bırak") { dialog, _ ->
                setAutoSwitch(false)
                dialog.dismiss()
            }
    }
}

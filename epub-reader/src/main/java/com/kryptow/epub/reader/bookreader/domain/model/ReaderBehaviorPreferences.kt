package com.kryptow.epub.reader.bookreader.domain.model

/**
 * Okuyucunun dokunma / donanım tuşu / parlaklık / hedef davranışları.
 *
 * [ReadingPreferences]'tan bilerek ayrı tutulur: o sınıf değiştiğinde WebView içeriği
 * yeniden yüklenir ve pagination sıfırlanır. Parlaklık kaydırıcısı gibi sık değişen
 * değerlerin bunu tetiklememesi gerekir.
 */
data class ReaderBehaviorPreferences(
    /** Sayfa modunda: sol kenar → önceki sayfa, sağ kenar → sonraki sayfa, orta → menü. */
    val tapZonesEnabled: Boolean = true,
    /** Ses açma/kısma tuşlarıyla sayfa çevirme. */
    val volumeKeysEnabled: Boolean = true,
    /** Ses tuşlarının yönünü ters çevir (açma = sonraki). */
    val volumeKeysInverted: Boolean = false,
    /**
     * Okuyucu ekran parlaklığı, 0.01f..1f.
     * [BRIGHTNESS_SYSTEM] → sistem parlaklığı kullanılır.
     */
    val brightness: Float = BRIGHTNESS_SYSTEM,
    /** Günlük okuma hedefi (dakika). 0 = kapalı. */
    val dailyGoalMinutes: Int = 20,
) {
    val usesSystemBrightness: Boolean get() = brightness < 0f

    companion object {
        const val BRIGHTNESS_SYSTEM = -1f
        const val MIN_BRIGHTNESS = 0.01f
    }
}

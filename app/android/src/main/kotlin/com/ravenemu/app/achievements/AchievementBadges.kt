package com.ravenemu.app.achievements

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.widget.ImageView
import com.ravenemu.app.BuildConfig
import com.ravenemu.app.R
import java.io.File
import java.net.URL
import java.util.concurrent.Executors
import javax.net.ssl.HttpsURLConnection

/**
 * Badges des succès et portraits de compte.
 *
 * Un succès obtenu sans son badge reste un texte ; c'est l'image que le joueur
 * reconnaît. Elle arrive du réseau, donc tard, et parfois jamais : la vue
 * affiche son texte tout de suite et l'image s'y pose quand elle est là.
 *
 * Trois gardes encadrent ce chargement, parce qu'il part d'une adresse que le
 * serveur a nommée : [AchievementImageUrl] décide de l'hôte, la réponse est
 * bornée en octets, et le décodage est borné en pixels. Une réponse qui n'est
 * pas une image ne donne rien plutôt qu'une exception.
 *
 * Les images reviennent souvent : la même liste de succès se rouvre, et le même
 * badge s'affiche dans la liste puis dans la bannière. Deux caches l'évitent,
 * l'un en mémoire pour la session, l'autre sur disque dans le cache de
 * l'application, que le système peut vider quand il veut.
 */
internal class AchievementBadges private constructor(context: Context) {

    private val main = Handler(Looper.getMainLooper())
    private val workers = Executors.newFixedThreadPool(2) {
        Thread(it, "RavenEmu-RA-Image").apply { isDaemon = true; priority = Thread.MIN_PRIORITY }
    }
    private val directory = File(context.cacheDir, "retroachievements-images")
    /**
     * Cache mémoire borné, du plus ancien consulté au plus récent. Un badge
     * fait quelques kilooctets une fois décodé ; la borne existe pour qu'une
     * longue liste de succès ne les garde pas tous.
     */
    private val memory = object : LinkedHashMap<String, Bitmap>(0, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Bitmap>?) = size > MEMORY_ENTRIES
    }

    /**
     * Pose l'image de [rawUrl] dans [target] quand elle est disponible.
     *
     * La vue garde l'adresse demandée en étiquette. Une liste réutilise ses
     * lignes en défilant : sans cette vérification au moment de poser l'image,
     * un badge arrivé en retard se retrouverait sur le succès suivant.
     */
    fun into(target: ImageView, rawUrl: String?) {
        val url = AchievementImageUrl.sanitize(rawUrl)
        target.setTag(R.id.retroachievements_image_url, url)
        if (url == null) return
        cached(url)?.let { target.setImageBitmap(it); return }
        workers.execute {
            val bitmap = runCatching { load(url) }.getOrNull() ?: return@execute
            main.post {
                if (target.getTag(R.id.retroachievements_image_url) == url) target.setImageBitmap(bitmap)
            }
        }
    }

    private fun cached(url: String): Bitmap? = synchronized(memory) { memory[url] }

    private fun load(url: String): Bitmap? {
        cached(url)?.let { return it }
        val file = File(directory, AchievementImageUrl.cacheKey(url))
        val bytes = file.takeIf { it.isFile }?.let { runCatching { it.readBytes() }.getOrNull() }
            ?: download(url)?.also { store(file, it) }
            ?: return null
        val bitmap = decode(bytes) ?: return null
        synchronized(memory) { memory[url] = bitmap }
        return bitmap
    }

    /** Même durcissement que les appels d'API : HTTPS, sans redirection, borné. */
    private fun download(url: String): ByteArray? {
        var connection: HttpsURLConnection? = null
        return try {
            connection = (URL(url).openConnection() as HttpsURLConnection).apply {
                instanceFollowRedirects = false // Ne jamais suivre vers un autre hôte.
                connectTimeout = 10_000
                readTimeout = 15_000
                setRequestProperty("User-Agent", "RavenEmu/${BuildConfig.VERSION_NAME} (Android)")
            }
            if (connection.responseCode !in 200..299) return null
            connection.inputStream.use { input ->
                val output = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    if (output.size() + read > MAX_BYTES) return null
                    output.write(buffer, 0, read)
                }
                output.toByteArray()
            }
        } catch (_: Exception) {
            null // Pas d'image : la vue garde son texte. Rien n'est journalisé.
        } finally {
            connection?.disconnect()
        }
    }

    /**
     * Décode en bornant la taille. Un badge mesure 64 points de côté ; une
     * réponse qui en annoncerait des milliers épuiserait la mémoire avant
     * qu'on ait pu constater qu'elle n'avait rien à y faire.
     */
    private fun decode(bytes: ByteArray): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > MAX_PIXELS) sample *= 2
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply {
            inSampleSize = sample
        })
    }

    private fun store(file: File, bytes: ByteArray) {
        runCatching {
            directory.mkdirs()
            val temporary = File(directory, file.name + ".part")
            temporary.writeBytes(bytes)
            // Renommer plutôt qu'écrire en place : une écriture interrompue ne
            // laisse jamais un fichier de cache à moitié écrit sous son nom.
            if (!temporary.renameTo(file)) temporary.delete()
            trim()
        }
    }

    /** Le cache système peut tout effacer ; cette borne évite qu'il y soit obligé. */
    private fun trim() {
        val files = directory.listFiles() ?: return
        if (files.size <= DISK_ENTRIES) return
        files.sortedBy { it.lastModified() }.take(files.size - DISK_ENTRIES).forEach { it.delete() }
    }

    internal companion object {
        private const val MAX_BYTES = 512 * 1024
        private const val MAX_PIXELS = 256
        private const val MEMORY_ENTRIES = 96
        private const val DISK_ENTRIES = 512

        @Volatile private var shared: AchievementBadges? = null

        /** Une seule instance par application : les caches sont communs à tout l'écran. */
        fun of(context: Context): AchievementBadges = shared ?: synchronized(this) {
            shared ?: AchievementBadges(context.applicationContext).also { shared = it }
        }
    }
}

package com.voltai.doai.data.qwen

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.net.CookieHandler
import java.net.CookieManager
import java.net.CookiePolicy
import java.net.CookieStore
import java.net.HttpCookie
import java.net.URI

/**
 * Stock de cookies persistant (SharedPreferences) utilisé par le
 * CookieManager global de l'application. Les cookies reçus du serveur
 * (tunnel Cloudflare, session Google/Colab…) sont conservés entre deux
 * lancements : l'application se reconnecte sans re-saisie.
 */
class PersistentCookieStore(context: Context) : CookieStore {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun add(uri: URI?, cookie: HttpCookie) {
        if (cookie.hasExpired()) return
        val cookies = load()
        if (cookie.domain.isNullOrBlank() && uri?.host != null) {
            cookie.domain = uri.host
        }
        cookies.removeAll { it.name == cookie.name && it.domain == cookie.domain && it.path == cookie.path }
        cookies.add(cookie)
        save(cookies)
    }

    override fun get(uri: URI?): List<HttpCookie> {
        if (uri == null) return emptyList()
        val host = uri.host?.lowercase() ?: return emptyList()
        val path = uri.path.ifEmpty { "/" }
        val cookies = load()
        return cookies.filter { cookie ->
            if (cookie.hasExpired()) return@filter false
            val domain = cookie.domain?.lowercase()?.removePrefix(".") ?: host
            val hostMatch = host == domain || host.endsWith(".$domain")
            val cookiePath = cookie.path ?: "/"
            val pathMatch = path.startsWith(cookiePath) ||
                cookiePath.startsWith("/") && path == cookiePath.removeSuffix("/")
            hostMatch && pathMatch
        }
    }

    override fun getCookies(): List<HttpCookie> =
        load().filter { !it.hasExpired() }

    override fun getURIs(): List<URI> =
        load().mapNotNull { cookie ->
            runCatching {
                val scheme = if (cookie.secure) "https" else "http"
                URI("$scheme://${cookie.domain ?: "localhost"}${cookie.path ?: "/"}")
            }.getOrNull()
        }

    override fun remove(uri: URI?, cookie: HttpCookie): Boolean {
        val cookies = load()
        val removed = cookies.removeAll {
            it.name == cookie.name && it.domain == cookie.domain && it.path == cookie.path
        }
        if (removed) save(cookies)
        return removed
    }

    override fun removeAll(): Boolean {
        val hadCookies = load().isNotEmpty()
        prefs.edit().remove(KEY_COOKIES).apply()
        return hadCookies
    }

    private fun save(cookies: Collection<HttpCookie>) {
        val array = JSONArray()
        for (cookie in cookies) {
            val entry = JSONObject()
            entry.put("name", cookie.name)
            entry.put("value", cookie.value)
            entry.put("domain", cookie.domain.orEmpty())
            entry.put("path", cookie.path.orEmpty())
            val expires = if (cookie.maxAge > 0) {
                System.currentTimeMillis() + cookie.maxAge * 1000L
            } else {
                0L
            }
            entry.put("expires", expires)
            entry.put("secure", cookie.secure)
            entry.put("httponly", cookie.isHttpOnly)
            array.put(entry)
        }
        prefs.edit().putString(KEY_COOKIES, array.toString()).apply()
    }

    private fun load(): MutableList<HttpCookie> {
        val raw = prefs.getString(KEY_COOKIES, null) ?: return mutableListOf()
        return runCatching {
            val array = JSONArray(raw)
            val out = mutableListOf<HttpCookie>()
            for (i in 0 until array.length()) {
                val entry = array.getJSONObject(i)
                val cookie = HttpCookie(entry.getString("name"), entry.getString("value"))
                cookie.domain = entry.getString("domain")
                cookie.path = entry.getString("path").ifEmpty { "/" }
                val expires = entry.optLong("expires")
                cookie.maxAge = if (expires > 0) (expires - System.currentTimeMillis()) / 1000L else -1L
                cookie.secure = entry.optBoolean("secure")
                cookie.isHttpOnly = entry.optBoolean("httponly")
                if (!cookie.hasExpired()) out.add(cookie)
            }
            out
        }.getOrDefault(mutableListOf())
    }

    companion object {
        private const val PREFS_NAME = "voltai_cookie_store"
        private const val KEY_COOKIES = "cookies"

        /** Installe le CookieManager persistant comme gestionnaire HTTP global. */
        fun install(context: Context) {
            if (CookieHandler.getDefault() == null) {
                CookieHandler.setDefault(
                    CookieManager(
                        PersistentCookieStore(context),
                        CookiePolicy.ACCEPT_ALL
                    )
                )
            }
        }

        fun clear(context: Context) {
            PersistentCookieStore(context).removeAll()
        }
    }
}
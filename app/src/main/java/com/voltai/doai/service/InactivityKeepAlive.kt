package com.voltai.doai.service

import com.voltai.doai.data.qwen.QwenClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.random.Random

/**
 * Garde le serveur Qwen (tunnel Cloudflare + llama-server Colab) actif
 * pendant les périodes d'inactivité de l'utilisateur : après [idleDelayMs]
 * sans interaction, des mini-requêtes aléatoires sont envoyées à intervalle
 * aléatoire pour éviter que le tunnel/serveur ne se déconnecte. Dès que
 * l'utilisateur interagit à nouveau, l'envoi s'arrête immédiatement.
 */
class InactivityKeepAlive(
    private val qwenClient: QwenClient,
    private val scope: CoroutineScope,
    private val idleDelayMs: Long = 30_000L,
    private val minIntervalMs: Long = 25_000L,
    private val maxIntervalMs: Long = 90_000L
) {

    @Volatile
    private var lastActivity = System.currentTimeMillis()

    private var idleJob: Job? = null
    private var activityJob: Job? = null

    /** À appeler dès que l'utilisateur touche l'écran. */
    fun onUserActivity() {
        lastActivity = System.currentTimeMillis()
        activityJob?.cancel()
        activityJob = null
        monitor()
    }

    fun start() {
        lastActivity = System.currentTimeMillis()
        monitor()
    }

    fun stop() {
        idleJob?.cancel()
        activityJob?.cancel()
        idleJob = null
        activityJob = null
    }

    private fun monitor() {
        idleJob?.cancel()
        idleJob = scope.launch {
            while (isActive) {
                val elapsed = System.currentTimeMillis() - lastActivity
                if (elapsed >= idleDelayMs) {
                    activityJob?.cancel()
                    activityJob = launch { sendRandomActivities() }
                    break
                }
                delay(1_000L)
            }
        }
    }

    private suspend fun sendRandomActivities() {
        while (currentCoroutineContext().isActive) {
            val elapsed = System.currentTimeMillis() - lastActivity
            if (elapsed < idleDelayMs) break
            delay(Random.nextLong(minIntervalMs, maxIntervalMs))
            if (System.currentTimeMillis() - lastActivity < idleDelayMs) break
            when (Random.nextInt(3)) {
                0 -> runCatching { qwenClient.health() }
                1 -> runCatching { qwenClient.checkModel() }
                else -> runCatching { qwenClient.complete("ping", maxTokens = 1) }
            }
        }
    }
}
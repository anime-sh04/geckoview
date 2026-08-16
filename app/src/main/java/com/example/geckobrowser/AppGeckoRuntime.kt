package com.example.geckobrowser

import android.content.Context
import org.mozilla.geckoview.GeckoRuntime

/**
 * A [GeckoRuntime] is expensive to create and GeckoView only supports a single
 * runtime per process, so we lazily create one and hold on to it for the
 * lifetime of the app instead of creating a new one per Activity instance.
 */
object AppGeckoRuntime {

    @Volatile
    private var runtime: GeckoRuntime? = null

    fun get(context: Context): GeckoRuntime {
        return runtime ?: synchronized(this) {
            runtime ?: GeckoRuntime.create(context.applicationContext).also { runtime = it }
        }
    }
}

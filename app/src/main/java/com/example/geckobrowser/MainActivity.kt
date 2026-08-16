package com.example.geckobrowser

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import com.example.geckobrowser.databinding.ActivityMainBinding
import org.json.JSONObject
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoView
import org.mozilla.geckoview.WebExtension
import org.mozilla.geckoview.WebRequestError

private const val TAG = "GeckoBrowser"

/** Location of the bundled extension inside app/src/main/assets/extension/ */
private const val EXTENSION_LOCATION = "resource://android/assets/extension/"

/** Must match browser_specific_settings.gecko.id in assets/extension/manifest.json */
private const val EXTENSION_ID = "geckobrowser-example@example.com"

/**
 * Native-app id the extension connects to via browser.runtime.connectNative("browser").
 * This is GeckoView's own app<->extension messaging channel, not related to
 * desktop Firefox's external native-messaging-host mechanism.
 */
private const val NATIVE_APP_ID = "browser"

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private lateinit var runtime: GeckoRuntime
    private var geckoSession: GeckoSession? = null
    private var geckoView: GeckoView? = null
    private var extensionPort: WebExtension.Port? = null

    private var canGoBackInBrowser = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        runtime = AppGeckoRuntime.get(this)
        installExtension()

        binding.startButton.setOnClickListener { showBrowser() }
        binding.errorRetryButton.setOnClickListener { loadTargetUrl() }
        binding.errorHomeButton.setOnClickListener { showHome() }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when {
                    canGoBackInBrowser -> geckoSession?.goBack()
                    binding.browserContainer.visibility == android.view.View.VISIBLE -> showHome()
                    else -> {
                        isEnabled = false
                        onBackPressedDispatcher.onBackPressed()
                    }
                }
            }
        })
    }

    // ------------------------------------------------------------------
    // WebExtension install + Android <-> extension messaging
    // ------------------------------------------------------------------

    private fun installExtension() {
        runtime.webExtensionController
            .ensureBuiltIn(EXTENSION_LOCATION, EXTENSION_ID)
            .accept({ extension ->
                if (extension == null) return@accept
                Log.i(TAG, "WebExtension installed: ${extension.id}")

                extension.setMessageDelegate(object : WebExtension.MessageDelegate {
                    override fun onConnect(port: WebExtension.Port) {
                        Log.i(TAG, "Extension opened a native port")
                        extensionPort = port
                        port.setDelegate(object : WebExtension.PortDelegate {
                            override fun onPortMessage(message: Any, port: WebExtension.Port) {
                                Log.i(TAG, "Message from extension: $message")
                            }

                            override fun onDisconnect(port: WebExtension.Port) {
                                Log.i(TAG, "Extension port disconnected")
                                if (extensionPort === port) {
                                    extensionPort = null
                                }
                            }
                        })

                        // Demonstration message: Android -> extension.
                        port.postMessage(JSONObject().apply { put("type", "START") })
                    }
                }, NATIVE_APP_ID)
            }, { exception ->
                Log.e(TAG, "Failed to install bundled WebExtension", exception)
            })
    }

    /** Example of sending an on-demand message from the app to the extension. */
    private fun sendStartMessageToExtension() {
        extensionPort?.postMessage(JSONObject().apply { put("type", "START") })
    }

    // ------------------------------------------------------------------
    // Home <-> browser screen switching
    // ------------------------------------------------------------------

    private fun showHome() {
        binding.homeContainer.visibility = android.view.View.VISIBLE
        binding.browserContainer.visibility = android.view.View.GONE
    }

    private fun showBrowser() {
        binding.homeContainer.visibility = android.view.View.GONE
        binding.browserContainer.visibility = android.view.View.VISIBLE
        binding.errorContainer.visibility = android.view.View.GONE

        ensureSession()
        loadTargetUrl()
    }

    private fun ensureSession() {
        if (geckoSession != null) return

        val session = GeckoSession()
        session.open(runtime)
        attachDelegates(session)
        geckoSession = session

        val view = GeckoView(this)
        view.setSession(session)
        binding.geckoViewHolder.removeAllViews()
        binding.geckoViewHolder.addView(view)
        geckoView = view
    }

    private fun loadTargetUrl() {
        binding.errorContainer.visibility = android.view.View.GONE
        geckoSession?.loadUri(getString(R.string.target_url))
    }

    // ------------------------------------------------------------------
    // GeckoSession delegates: loading indicator, navigation, errors
    // ------------------------------------------------------------------

    private fun attachDelegates(session: GeckoSession) {
        session.progressDelegate = object : GeckoSession.ProgressDelegate {
            override fun onPageStart(session: GeckoSession, url: String) {
                binding.loadingIndicator.visibility = android.view.View.VISIBLE
                binding.errorContainer.visibility = android.view.View.GONE
            }

            override fun onPageStop(session: GeckoSession, success: Boolean) {
                binding.loadingIndicator.visibility = android.view.View.GONE
            }
        }

        session.navigationDelegate = object : GeckoSession.NavigationDelegate {
            override fun onCanGoBack(session: GeckoSession, canGoBack: Boolean) {
                canGoBackInBrowser = canGoBack
            }

            override fun onLoadError(
                session: GeckoSession,
                uri: String?,
                error: WebRequestError
            ): GeckoResult<String>? {
                runOnUiThread {
                    binding.loadingIndicator.visibility = android.view.View.GONE
                    binding.errorContainer.visibility = android.view.View.VISIBLE
                    binding.errorMessage.text = describeError(error, uri)
                }
                // Returning null tells GeckoView not to load a replacement error page;
                // we render our own native error UI instead.
                return GeckoResult.fromValue(null)
            }
        }
    }

    private fun describeError(error: WebRequestError, uri: String?): String {
        val target = uri ?: getString(R.string.target_url)
        return "$target\n\n(error code: ${error.code}, category: ${error.category})"
    }

    override fun onDestroy() {
        geckoSession?.close()
        super.onDestroy()
    }
}

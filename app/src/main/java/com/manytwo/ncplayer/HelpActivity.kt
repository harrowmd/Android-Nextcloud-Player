package com.manytwo.ncplayer

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.addCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import org.commonmark.ext.gfm.tables.TablesExtension
import org.commonmark.ext.heading.anchor.HeadingAnchorExtension
import org.commonmark.parser.Parser
import org.commonmark.renderer.html.HtmlRenderer

/** Shows the user guide bundled in the APK (assets/USERGUIDE.md), so Help works without internet. */
class HelpActivity : AppCompatActivity() {
    private lateinit var web: WebView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        web = WebView(this)
        // A WebView ignores its own padding, so keep it clear of the status and navigation bars with a frame.
        val frame = android.widget.FrameLayout(this).apply {
            setBackgroundColor(com.google.android.material.color.MaterialColors.getColor(
                this@HelpActivity, com.google.android.material.R.attr.colorSurface, 0))
            addView(web)
        }
        setContentView(frame)
        ViewCompat.setOnApplyWindowInsetsListener(frame) { v, insets ->
            val i = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updatePadding(left = i.left, top = i.top, right = i.right, bottom = i.bottom)
            insets
        }
        web.webViewClient = object : WebViewClient() {
            // Links within the guide stay here; web links open in the browser.
            override fun shouldOverrideUrlLoading(view: WebView, req: WebResourceRequest): Boolean {
                val u = req.url
                if (u.scheme == "http" || u.scheme == "https") {
                    runCatching { startActivity(Intent(Intent.ACTION_VIEW, u)) }
                    return true
                }
                return false
            }
        }
        onBackPressedDispatcher.addCallback(this) { if (web.canGoBack()) web.goBack() else finish() }
        web.loadDataWithBaseURL("file:///android_asset/", page(), "text/html", "utf-8", null)
    }

    private fun page(): String {
        val md = assets.open("USERGUIDE.md").bufferedReader().use { it.readText() }
        val ext = listOf(TablesExtension.create(), HeadingAnchorExtension.create())
        val body = HtmlRenderer.builder().extensions(ext).build()
            .render(Parser.builder().extensions(ext).build().parse(md))
        return """<!doctype html><html><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<style>
:root { color-scheme: light dark; --fg:#1c1b1f; --bg:#fffbfe; --muted:#5f5b66; --line:#d8d4dc; --link:#1565c0; --code:#eee9f2; }
@media (prefers-color-scheme: dark) { :root { --fg:#e6e1e5; --bg:#1c1b1f; --muted:#aaa4b0; --line:#49454f; --link:#90caf9; --code:#2b2930; } }
body { font-family: sans-serif; font-size: 17px; line-height: 1.5; color: var(--fg); background: var(--bg); margin: 0; padding: 12px 16px 40px; }
h1 { font-size: 1.5em; } h2 { font-size: 1.25em; margin-top: 1.6em; border-bottom: 1px solid var(--line); padding-bottom: .2em; }
a { color: var(--link); }
code { background: var(--code); padding: 1px 4px; border-radius: 4px; font-size: .92em; word-break: break-word; }
table { border-collapse: collapse; width: 100%; font-size: .95em; }
th, td { border: 1px solid var(--line); padding: 6px; text-align: left; vertical-align: top; }
hr { border: none; border-top: 1px solid var(--line); }
.ver { color: var(--muted); font-size: .9em; }
</style></head><body>
<p class="ver">Version ${BuildConfig.VERSION_NAME} · <a href="${Updater.HELP_URL}">latest guide online</a></p>
$body
</body></html>"""
    }
}

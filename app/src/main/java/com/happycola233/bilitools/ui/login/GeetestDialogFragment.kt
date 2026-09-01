package com.happycola233.bilitools.ui.login

import android.annotation.SuppressLint
import android.graphics.Color
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.util.Size
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.core.graphics.drawable.toDrawable
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.DialogFragment
import com.happycola233.bilitools.R
import com.happycola233.bilitools.core.BiliHttpClient
import com.happycola233.bilitools.data.CaptchaResult
import com.happycola233.bilitools.databinding.DialogGeetestBinding
import org.json.JSONObject
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.roundToInt

class GeetestDialogFragment : DialogFragment() {
    interface Listener {
        fun onCaptchaSuccess(result: CaptchaResult)
        fun onCaptchaError(message: String?)
        fun onCaptchaCancel()
    }

    private var _binding: DialogGeetestBinding? = null
    private val binding get() = _binding!!
    private var hasSizedCaptchaPanel = false
    private var hasDispatchedTerminalEvent = false
    var listener: Listener? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NO_TITLE, 0)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = DialogGeetestBinding.inflate(inflater, container, false)
        return binding.root
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val gt = requireArguments().getString(ARG_GT).orEmpty()
        val challenge = requireArguments().getString(ARG_CHALLENGE).orEmpty()
        if (gt.isBlank() || challenge.isBlank()) {
            finishWithError(getString(R.string.login_error_captcha_failed))
            return
        }

        ViewCompat.setAccessibilityPaneTitle(binding.root, getString(R.string.login_captcha_title))
        resizeDialogContent(availableDialogSize())

        binding.geetestWebview.apply {
            setBackgroundColor(Color.TRANSPARENT)
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                useWideViewPort = true
                loadWithOverviewMode = true
                loadsImagesAutomatically = true
                cacheMode = WebSettings.LOAD_NO_CACHE
                mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                allowFileAccess = false
                allowContentAccess = false
                setSupportZoom(false)
                userAgentString = BiliHttpClient.USER_AGENT
            }
            CookieManager.getInstance().setAcceptCookie(true)
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
            webViewClient = object : WebViewClient() {
                override fun onReceivedError(
                    view: WebView,
                    request: WebResourceRequest,
                    error: WebResourceError,
                ) {
                    if (request.isForMainFrame) {
                        finishWithError(error.description?.toString())
                    }
                }
            }
            webChromeClient = WebChromeClient()
            addJavascriptInterface(CaptchaBridge(), CAPTCHA_BRIDGE_NAME)
            loadDataWithBaseURL(
                BASE_URL,
                buildHtml(gt, challenge),
                "text/html",
                "utf-8",
                BASE_URL,
            )
        }
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.apply {
            setBackgroundDrawable(Color.TRANSPARENT.toDrawable())
            decorView.setPadding(0, 0, 0, 0)
            setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)
        }
        if (!hasSizedCaptchaPanel) {
            resizeDialogContent(availableDialogSize())
        } else {
            binding.root.layoutParams?.let { layoutParams ->
                dialog?.window?.setLayout(layoutParams.width, layoutParams.height)
            }
        }
    }

    override fun onCancel(dialog: android.content.DialogInterface) {
        super.onCancel(dialog)
        if (hasDispatchedTerminalEvent) return
        hasDispatchedTerminalEvent = true
        listener?.onCaptchaCancel()
    }

    override fun onDestroyView() {
        _binding?.geetestWebview?.apply {
            stopLoading()
            removeJavascriptInterface(CAPTCHA_BRIDGE_NAME)
            webChromeClient = null
            removeAllViews()
            destroy()
        }
        _binding = null
        super.onDestroyView()
    }

    private fun availableDialogSize(): Size {
        val activity = requireActivity()
        val displayMetrics = resources.displayMetrics
        val windowBounds =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                activity.windowManager.currentWindowMetrics.bounds
            } else {
                Rect(0, 0, displayMetrics.widthPixels, displayMetrics.heightPixels)
            }
        val safeInsets =
            ViewCompat.getRootWindowInsets(activity.window.decorView)
                ?.getInsets(
                    WindowInsetsCompat.Type.systemBars() or
                        WindowInsetsCompat.Type.displayCutout(),
                )
        val horizontalInsets = (safeInsets?.left ?: 0) + (safeInsets?.right ?: 0)
        val verticalInsets = (safeInsets?.top ?: 0) + (safeInsets?.bottom ?: 0)
        val outerMargin = (DIALOG_OUTER_MARGIN_DP * displayMetrics.density).roundToInt()
        return Size(
            (windowBounds.width() - horizontalInsets - outerMargin * 2).coerceAtLeast(1),
            (windowBounds.height() - verticalInsets - outerMargin * 2).coerceAtLeast(1),
        )
    }

    private fun resizeDialogContent(size: Size) {
        val rootLayoutParams =
            binding.root.layoutParams ?: ViewGroup.LayoutParams(size.width, size.height)
        binding.root.layoutParams = rootLayoutParams.apply {
            width = size.width
            height = size.height
        }
        binding.root.requestLayout()
        dialog?.window?.setLayout(size.width, size.height)
    }

    private fun showSizedCaptchaPanel(
        contentWidthCssPx: Double,
        contentHeightCssPx: Double,
        viewportWidthCssPx: Double,
        viewportHeightCssPx: Double,
    ) {
        if (
            !contentWidthCssPx.isFinite() ||
            !contentHeightCssPx.isFinite() ||
            !viewportWidthCssPx.isFinite() ||
            !viewportHeightCssPx.isFinite() ||
            contentWidthCssPx <= 0.0 ||
            contentHeightCssPx <= 0.0 ||
            viewportWidthCssPx <= 0.0 ||
            viewportHeightCssPx <= 0.0
        ) {
            return
        }

        activity?.runOnUiThread {
            val currentBinding = _binding ?: return@runOnUiThread
            val webView = currentBinding.geetestWebview
            if (webView.width <= 0 || webView.height <= 0) return@runOnUiThread

            // CSS 像素会受 WebView 当前页面缩放影响，使用实际视口比例换算比直接套屏幕密度更可靠。
            val targetWidth =
                ceil(contentWidthCssPx * webView.width / viewportWidthCssPx).toInt()
            val targetHeight =
                ceil(contentHeightCssPx * webView.height / viewportHeightCssPx).toInt()
            val availableSize = availableDialogSize()
            resizeDialogContent(
                Size(
                    targetWidth.coerceAtMost(availableSize.width),
                    targetHeight.coerceAtMost(availableSize.height),
                ),
            )
            hasSizedCaptchaPanel = true
            webView.visibility = View.VISIBLE
            currentBinding.geetestLoading.visibility = View.GONE
        }
    }

    private fun buildHtml(gt: String, challenge: String): String {
        val localeLanguage = Locale.getDefault().language.lowercase(Locale.US)
        val geetestLanguage = if (localeLanguage.startsWith("zh")) "zh-cn" else localeLanguage
        val gtJson = JSONObject.quote(gt)
        val challengeJson = JSONObject.quote(challenge)
        val languageJson = JSONObject.quote(geetestLanguage)
        return """
            <!DOCTYPE html>
            <html lang="zh">
            <head>
              <meta charset="utf-8" />
              <meta name="viewport" content="width=device-width, initial-scale=1.0" />
              <script src="https://static.geetest.com/static/js/gt.0.4.9.js"
                onerror="CaptchaBridge.onError('Geetest load failed')"></script>
              <style>
                :root { --captcha-panel-scale: 1; }
                html, body {
                  width: 100%;
                  height: 100%;
                  margin: 0;
                  overflow: hidden;
                  background: transparent;
                }
                .geetest_panel,
                .geetest_panel_ghost {
                  /* 页面遮罩交给原生 Dialog，WebView 只绘制真正的验证码面板。 */
                  background: transparent !important;
                }
                .geetest_panel_box {
                  left: 50% !important;
                  top: 50% !important;
                  right: auto !important;
                  bottom: auto !important;
                  margin: 0 !important;
                  transform: translate(-50%, -50%) scale(var(--captcha-panel-scale)) !important;
                  transform-origin: center center !important;
                }
                .geetest_panel_box,
                .geetest_panel_next,
                .geetest_panel_next > .geetest_holder {
                  /* 极验入场动画会逐帧改变外框尺寸，关闭外框动画以避免原生窗口跟着跳动。 */
                  animation: none !important;
                  transition: none !important;
                }
              </style>
            </head>
            <body>
              <script>
                const PANEL_SELECTOR = ".geetest_panel_box";
                const PANEL_EDGE_PADDING = $PANEL_EDGE_PADDING_CSS_PX;
                // 原生窗口稍后会收缩，保留初始安全区域才能持续按屏幕上限计算缩放比例。
                let maximumViewportWidth = window.innerWidth;
                let maximumViewportHeight = window.innerHeight;
                let observedPanel = null;
                let panelResizeObserver = null;
                let panelFitTimer = null;
                let lastPanelScale = "";
                let lastReportedSize = "";

                function schedulePanelFit() {
                  if (panelFitTimer !== null) clearTimeout(panelFitTimer);
                  panelFitTimer = setTimeout(function () {
                    panelFitTimer = null;
                    requestAnimationFrame(fitAndReportPanel);
                  }, $PANEL_SIZE_STABILITY_DELAY_MS);
                }

                function observePanel(panel) {
                  if (panel === observedPanel) return;
                  if (panelResizeObserver) panelResizeObserver.disconnect();
                  observedPanel = panel;
                  panelResizeObserver = new ResizeObserver(schedulePanelFit);
                  panelResizeObserver.observe(panel);
                }

                function fitAndReportPanel() {
                  const panel = document.querySelector(PANEL_SELECTOR);
                  if (!panel) return;
                  observePanel(panel);

                  const panelOverlay = panel.closest(".geetest_panel");
                  if (panelOverlay && getComputedStyle(panelOverlay).display === "none") return;
                  const panelWidth = panel.offsetWidth;
                  const panelHeight = panel.offsetHeight;
                  if (panelWidth <= 0 || panelHeight <= 0) return;

                  maximumViewportWidth = Math.max(maximumViewportWidth, window.innerWidth);
                  maximumViewportHeight = Math.max(maximumViewportHeight, window.innerHeight);
                  const availableWidth = Math.max(1, maximumViewportWidth - PANEL_EDGE_PADDING * 2);
                  const availableHeight = Math.max(1, maximumViewportHeight - PANEL_EDGE_PADDING * 2);
                  // 提示、图片和操作区整体等比缩放，任何方向空间不足都不会裁掉局部内容。
                  const panelScale = Math.min(
                    1,
                    availableWidth / panelWidth,
                    availableHeight / panelHeight
                  );
                  const panelScaleValue = panelScale.toFixed(6);
                  if (panelScaleValue !== lastPanelScale) {
                    lastPanelScale = panelScaleValue;
                    document.documentElement.style.setProperty(
                      "--captcha-panel-scale",
                      panelScaleValue
                    );
                  }

                  const contentWidth = Math.ceil(panelWidth * panelScale + PANEL_EDGE_PADDING * 2);
                  const contentHeight = Math.ceil(panelHeight * panelScale + PANEL_EDGE_PADDING * 2);
                  const reportedSize = [
                    contentWidth,
                    contentHeight,
                    window.innerWidth,
                    window.innerHeight
                  ].join(":");
                  if (reportedSize === lastReportedSize) return;
                  lastReportedSize = reportedSize;
                  CaptchaBridge.onPanelSize(
                    contentWidth,
                    contentHeight,
                    window.innerWidth,
                    window.innerHeight
                  );
                }

                const panelMutationObserver = new MutationObserver(function (mutations) {
                  if (mutations.some(function (mutation) {
                    return mutation.target !== document.documentElement;
                  })) {
                    schedulePanelFit();
                  }
                });
                panelMutationObserver.observe(document.documentElement, {
                  attributes: true,
                  childList: true,
                  subtree: true,
                  attributeFilter: ["class", "style"]
                });
                window.addEventListener("resize", schedulePanelFit);

                function initCaptcha() {
                  if (typeof initGeetest !== "function") {
                    CaptchaBridge.onError("Geetest init failed");
                    return;
                  }
                  initGeetest({
                    gt: $gtJson,
                    challenge: $challengeJson,
                    offline: false,
                    new_captcha: true,
                    product: "bind",
                    width: "300px",
                    lang: $languageJson,
                    https: true,
                    onError: function (message) {
                      CaptchaBridge.onError(message || "Geetest init failed");
                    }
                  }, function (captchaObj) {
                    captchaObj.onReady(function () {
                      captchaObj.verify();
                      schedulePanelFit();
                    });
                    captchaObj.onSuccess(function () {
                      const result = captchaObj.getValidate();
                      if (result) {
                        CaptchaBridge.onSuccess(
                          result.geetest_challenge,
                          result.geetest_validate,
                          result.geetest_seccode
                        );
                      }
                    });
                    captchaObj.onError(function (error) {
                      CaptchaBridge.onError(error && error.msg ? error.msg : "");
                    });
                    captchaObj.onClose(function () {
                      CaptchaBridge.onCancel();
                    });
                  });
                }
                window.onload = initCaptcha;
              </script>
            </body>
            </html>
        """.trimIndent()
    }

    private fun finishWithError(message: String?) {
        dispatchTerminalEvent {
            listener?.onCaptchaError(message)
            dismiss()
        }
    }

    private fun dispatchTerminalEvent(block: () -> Unit) {
        activity?.runOnUiThread {
            if (hasDispatchedTerminalEvent || !isAdded) return@runOnUiThread
            hasDispatchedTerminalEvent = true
            block()
        }
    }

    private inner class CaptchaBridge {
        @JavascriptInterface
        fun onPanelSize(
            contentWidthCssPx: Double,
            contentHeightCssPx: Double,
            viewportWidthCssPx: Double,
            viewportHeightCssPx: Double,
        ) {
            showSizedCaptchaPanel(
                contentWidthCssPx,
                contentHeightCssPx,
                viewportWidthCssPx,
                viewportHeightCssPx,
            )
        }

        @JavascriptInterface
        fun onSuccess(challenge: String, validate: String, seccode: String) {
            dispatchTerminalEvent {
                listener?.onCaptchaSuccess(
                    CaptchaResult(
                        challenge = challenge,
                        validate = validate,
                        seccode = seccode,
                    ),
                )
                dismiss()
            }
        }

        @JavascriptInterface
        fun onError(message: String?) {
            finishWithError(message)
        }

        @JavascriptInterface
        fun onCancel() {
            dispatchTerminalEvent {
                listener?.onCaptchaCancel()
                dismiss()
            }
        }
    }

    companion object {
        private const val ARG_GT = "arg_gt"
        private const val ARG_CHALLENGE = "arg_challenge"
        private const val CAPTCHA_BRIDGE_NAME = "CaptchaBridge"
        private const val BASE_URL = "https://www.bilibili.com/"
        private const val DIALOG_OUTER_MARGIN_DP = 24
        private const val PANEL_EDGE_PADDING_CSS_PX = 8
        private const val PANEL_SIZE_STABILITY_DELAY_MS = 120

        fun newInstance(gt: String, challenge: String): GeetestDialogFragment {
            val fragment = GeetestDialogFragment()
            fragment.arguments = Bundle().apply {
                putString(ARG_GT, gt)
                putString(ARG_CHALLENGE, challenge)
            }
            return fragment
        }
    }
}

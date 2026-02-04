package com.happycola233.bilitools.ui.login

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.happycola233.bilitools.R
import com.happycola233.bilitools.core.BiliHttpClient
import com.happycola233.bilitools.data.CaptchaResult
import com.happycola233.bilitools.databinding.DialogGeetestBinding
import java.util.Locale

class GeetestDialogFragment : BottomSheetDialogFragment() {
    interface Listener {
        fun onCaptchaSuccess(result: CaptchaResult)
        fun onCaptchaError(message: String?)
        fun onCaptchaCancel()
    }

    private var _binding: DialogGeetestBinding? = null
    private val binding get() = _binding!!
    var listener: Listener? = null

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
            listener?.onCaptchaError(getString(R.string.login_error_captcha_failed))
            dismiss()
            return
        }
        val settings = binding.geetestWebview.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.javaScriptCanOpenWindowsAutomatically = true
        settings.useWideViewPort = true
        settings.loadWithOverviewMode = true
        settings.loadsImagesAutomatically = true
        settings.cacheMode = WebSettings.LOAD_NO_CACHE
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        settings.userAgentString = BiliHttpClient.USER_AGENT
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(binding.geetestWebview, true)
        binding.geetestWebview.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                binding.geetestLoading.visibility = View.GONE
            }

            override fun onReceivedError(
                view: WebView,
                request: android.webkit.WebResourceRequest,
                error: android.webkit.WebResourceError,
            ) {
                listener?.onCaptchaError(error.description?.toString())
            }
        }
        binding.geetestWebview.webChromeClient = WebChromeClient()
        binding.geetestWebview.addJavascriptInterface(
            CaptchaBridge(),
            "CaptchaBridge",
        )
        val html = buildHtml(gt, challenge)
        binding.geetestWebview.loadDataWithBaseURL(
            "https://www.bilibili.com/",
            html,
            "text/html",
            "utf-8",
            null,
        )
    }

    override fun onCancel(dialog: android.content.DialogInterface) {
        super.onCancel(dialog)
        listener?.onCaptchaCancel()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding.geetestWebview.stopLoading()
        binding.geetestWebview.removeAllViews()
        binding.geetestWebview.destroy()
        _binding = null
    }

    override fun onStart() {
        super.onStart()
        val sheetDialog = dialog as? BottomSheetDialog ?: return
        val bottomSheet =
            sheetDialog.findViewById<FrameLayout>(
                com.google.android.material.R.id.design_bottom_sheet,
            ) ?: return
        bottomSheet.layoutParams = bottomSheet.layoutParams.apply {
            height = ViewGroup.LayoutParams.MATCH_PARENT
        }
        val behavior = BottomSheetBehavior.from(bottomSheet)
        behavior.skipCollapsed = true
        behavior.state = BottomSheetBehavior.STATE_EXPANDED
        behavior.peekHeight = resources.displayMetrics.heightPixels
    }

    private fun buildHtml(gt: String, challenge: String): String {
        val lang = Locale.getDefault().language.lowercase(Locale.US)
        val langValue = if (lang.startsWith("zh")) "zh-cn" else lang
        return """
            <!DOCTYPE html>
            <html lang="zh">
            <head>
              <meta charset="utf-8" />
              <meta name="viewport" content="width=device-width, initial-scale=1.0" />
              <script src="https://static.geetest.com/static/js/gt.0.4.9.js"
                onerror="CaptchaBridge.onError('Geetest load failed')"></script>
              <style>
                html, body { height: 100%; margin: 0; }
                body { display: flex; justify-content: center; align-items: center; background: #fff; }
                #captcha { width: 100%; min-height: 280px; }
              </style>
            </head>
            <body>
              <div id="captcha"></div>
              <script>
                function initCaptcha() {
                  if (typeof initGeetest !== 'function') {
                    CaptchaBridge.onError('Geetest init failed');
                    return;
                  }
                  initGeetest({
                    gt: "$gt",
                    challenge: "$challenge",
                    offline: false,
                    new_captcha: true,
                    product: "bind",
                    width: "300px",
                    lang: "$langValue",
                    https: true
                  }, function (captchaObj) {
                    captchaObj.appendTo("#captcha");
                    captchaObj.onReady(function () {
                      captchaObj.verify();
                    });
                    captchaObj.onSuccess(function () {
                      var result = captchaObj.getValidate();
                      if (result) {
                        CaptchaBridge.onSuccess(
                          result.geetest_challenge,
                          result.geetest_validate,
                          result.geetest_seccode
                        );
                      }
                    });
                    captchaObj.onError(function (err) {
                      CaptchaBridge.onError(err && err.msg ? err.msg : "");
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

    private inner class CaptchaBridge {
        @JavascriptInterface
        fun onSuccess(challenge: String, validate: String, seccode: String) {
            listener?.onCaptchaSuccess(
                CaptchaResult(
                    challenge = challenge,
                    validate = validate,
                    seccode = seccode,
                ),
            )
            dismiss()
        }

        @JavascriptInterface
        fun onError(message: String?) {
            listener?.onCaptchaError(message)
            dismiss()
        }

        @JavascriptInterface
        fun onCancel() {
            listener?.onCaptchaCancel()
            dismiss()
        }
    }

    companion object {
        private const val ARG_GT = "arg_gt"
        private const val ARG_CHALLENGE = "arg_challenge"

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

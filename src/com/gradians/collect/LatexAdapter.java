package com.gradians.collect;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.graphics.Color;
import android.os.Build;
import android.support.v4.view.PagerAdapter;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.webkit.WebViewClient;

class LatexAdapter extends PagerAdapter {
    
    public LatexAdapter(String[] latex, Activity activity) {
        this.latex = latex;
        this.activity = activity;
    }

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    public Object instantiateItem(ViewGroup container, int position) {
        final String latexString = latex[position];
        final WebView webView = new WebView(activity);        
        final int scale = activity.getResources().getBoolean(R.bool.isTablet) ? 120 : 80;
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
                    webView.loadUrl("javascript:MathJax.Hub.Queue(['Typeset', MathJax.Hub]);");
                } else {
                    webView.evaluateJavascript("MathJax.Hub.Queue(['Typeset', MathJax.Hub]);", null);
                }
            }
        });
        webView.setBackgroundColor(Color.TRANSPARENT);
        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setLoadWithOverviewMode(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB)
            webView.getSettings().setDisplayZoomControls(false);
        webView.loadDataWithBaseURL("file:///android_asset/mathjax-svg",
            String.format(HTML, scale, latexString), "text/html", "utf-8", null);
            
        container.addView(webView);
        return webView;
    }

    @Override
    public void destroyItem(ViewGroup container, int position, Object object) {
        container.removeView((View)object);
    }

    @Override
    public int getCount() {
        return latex.length;
    }

    @Override
    public boolean isViewFromObject(View container, Object object) {
        return container == object;
    }
    
    private String[] latex;
    private Activity activity;
    
    private final String HTML = 
          "<html><head>"
        + "<script type='text/x-mathjax-config'>"
        +   "MathJax.Hub.Config({ "
        +     "showMathMenu: false,"
        +     "jax: [\"input/TeX\", \"output/SVG\"],"
        +     "extensions: [\"tex2jax.js\",\"mml2jax.js\",\"MathMenu.js\",\"MathZoom.js\"],"
        +     "TeX: { extensions: [\"AMSmath.js\", \"AMSsymbols.js\", \"noErrors.js\", \"noUndefined.js\"] }," 
        +     "SVG: { "
        +       "scale: %d,"
        +       "font: \"TeX\", "
        +       "linebreaks: { automatic: false, width: \"automatic\" }, "
        +       "styles: { "
        +         "\".MathJax_SVG svg > g, .MathJax_SVG_Display svg > g\": {"
        +           "fill: \"#FFF\","
        +           "stroke: \"#FFF\""
        +         "}"
        +       "}"
        +     "}"
        +   "});"
        + "MathJax.Hub.Register.StartupHook(\"SVG Jax Ready\", function() {"
        +   "var VARIANT = MathJax.OutputJax[\"SVG\"].FONTDATA.VARIANT;"
        +   "VARIANT[\"normal\"].fonts.unshift(\"MathJax_SansSerif\");"
        + "});"
        + "</script>"
        + "<script type='text/javascript' src='file:///android_asset/mathjax-svg/MathJax.js'></script>"
        + "</head><body><span id='math' style='position: absolute; color:white;'>\\[%s\\]</span></body></html>";
       
}
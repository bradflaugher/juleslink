# JulesLink — keep WebView bridges intact
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
-keepclassmembers class * extends android.webkit.WebViewClient {
    public void *(android.webkit.WebView, java.lang.String, *);
    public boolean *(android.webkit.WebView, java.lang.String);
}
-keepclassmembers class * extends android.webkit.WebChromeClient {
    public void *(android.webkit.WebView, java.lang.String, android.webkit.JsResult);
    public void *(android.webkit.WebView, java.lang.String, java.lang.String, android.webkit.JsResult);
    public boolean *(android.webkit.WebView, *);
}
-dontwarn android.webkit.**

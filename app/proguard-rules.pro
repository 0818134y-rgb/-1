# kotlinx.serialization と Play services は各ライブラリ同梱のルールで足りる
-keepattributes *Annotation*, InnerClasses

# WebView の通信スクリプトから呼ぶ関数を消さない
-keepclassmembers class * { @android.webkit.JavascriptInterface <methods>; }

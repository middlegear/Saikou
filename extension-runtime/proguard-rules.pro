# Extension APKs are loaded reflectively by name, so the extension API surface
# and the runtime host entry points must survive shrinking.
-keep class com.anymex.runtimehost.** { *; }
-keep class eu.kanade.tachiyomi.** { *; }
-keep class com.lagradost.** { *; }
-keep class org.koitharu.kotatsu.** { *; }
-keep class uy.kohesive.injekt.** { *; }
-keep class kotlinx.serialization.** { *; }
-keepattributes Signature,InnerClasses,EnclosingMethod,*Annotation*
-dontwarn kotlinx.serialization.**
-dontwarn org.mozilla.**
-dontwarn com.fasterxml.jackson.**
-dontwarn com.uwetrottmann.**
-dontwarn org.nanohttpd.**
-dontwarn com.github.TeamNewPipe.**
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

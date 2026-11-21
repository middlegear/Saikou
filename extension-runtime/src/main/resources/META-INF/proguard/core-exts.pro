-keepattributes Exceptions,Signature,InnerClasses,EnclosingMethod

-keep class kotlin.** { *; }
-keep class kotlinx.coroutines.** { *; }

-keep class okhttp3.** { *; }
-keep class okio.** { *; }
-keep class org.jsoup.** { *; }

-keep class androidx.collection.** { *; }
-keep class org.json.** { *; }

-keep class org.koitharu.kotatsu.parsers.MangaLoaderContext { *; }
-keep class * extends org.koitharu.kotatsu.parsers.MangaLoaderContext { *; }
-keep interface org.koitharu.kotatsu.parsers.MangaParser { *; }
-keep interface org.koitharu.kotatsu.parsers.MangaSourceParser { *; }

-keepclassmembers class kotlin.jvm.internal.Intrinsics {
	public static void checkExpressionValueIsNotNull(...);
	public static void checkNotNullExpressionValue(...);
	public static void checkReturnedValueIsNotNull(...);
	public static void checkFieldIsNotNull(...);
	public static void checkParameterIsNotNull(...);
	public static void checkNotNullParameter(...);
}

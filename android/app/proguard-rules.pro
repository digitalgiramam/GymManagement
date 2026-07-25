# Retrofit + OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn retrofit2.**
-keep class retrofit2.** { *; }
-keepattributes Signature
-keepattributes Exceptions

# Gson data models — keep all fields so JSON parsing works after minification
-keepclassmembers class com.gymmanager.data.model.** { *; }
-keep class com.gymmanager.data.model.** { *; }

# ViewBinding — keep generated binding classes
-keep class com.gymmanager.databinding.** { *; }

# Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

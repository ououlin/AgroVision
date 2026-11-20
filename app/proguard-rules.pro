# ProGuard Rules for AgroVision

# ===== Room / Domain Entities =====
-keep class com.agrovision.app.data.local.entity.** { *; }
-keep class com.agrovision.app.domain.model.** { *; }

# ===== ONNX Runtime =====
-keep class ai.onnxruntime.** { *; }
-dontwarn ai.onnxruntime.**

# ===== TensorFlow Lite =====
-keep class org.tensorflow.lite.** { *; }
-dontwarn org.tensorflow.lite.**

# ===== Retrofit =====
-keepattributes Signature
-keepattributes *Annotation*
-keep class retrofit2.** { *; }
-keepclasseswithmembers class * {
    @retrofit2.http.* <methods>;
}
-dontwarn retrofit2.**

# ===== OkHttp / Okio =====
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**

# ===== Gson DTO =====
-keep class com.agrovision.app.data.remote.** { *; }
-keep class com.agrovision.app.data.llm.** { *; }
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# ===== Hilt / Dagger =====
-keep class dagger.hilt.** { *; }
-keep class dagger.internal.** { *; }
-dontwarn dagger.hilt.**

# ===== Kotlin Metadata =====
-keep class kotlin.Metadata { *; }
-keepattributes KotlinMetadata

# ===== Coroutines =====
-dontwarn kotlinx.coroutines.**

# ===== Compose (Jetpack Compose / Material3) =====
-keep class androidx.compose.runtime.** { *; }
-keep class androidx.compose.ui.** { *; }
-keep class androidx.compose.material3.** { *; }
-keep class androidx.compose.animation.** { *; }
-keep class androidx.compose.foundation.** { *; }
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod
-keepnames class androidx.compose.runtime.Composer
# Compose 生成的 ViewModel stubs / Stability 推断配置类
-keep class **$$ExternalSyntheticOutline0 { *; }
-keep class **$$Lambda$* { *; }
-dontwarn androidx.compose.**

# ===== Hilt / Dagger (生成类通过反射，必须 keep) =====
-keep class dagger.hilt.** { *; }
-keep class dagger.internal.** { *; }
-keep class dagger.**$$* { *; }
-keep class **_Hilt* { *; }
-keep class **$$Hilt* { *; }
-keepclasseswithmembers class * {
    @dagger.hilt.android.AndroidEntryPoint <fields>;
    @dagger.hilt.android.AndroidEntryPoint <methods>;
}
-keep class androidx.hilt.** { *; }
-dontwarn dagger.hilt.**

# ===== CameraX =====
-keep class androidx.camera.** { *; }
-dontwarn androidx.camera.**

# ===== ML Kit (文本识别 / OCR) =====
-keep class com.google.mlkit.** { *; }
-keep class com.google.android.gms.** { *; }
-keep class com.google.mlkit.vision.text.chinese.** { *; }
-dontwarn com.google.mlkit.**
-dontwarn com.google.android.gms.**

# ===== Play Services Location =====
-keep class com.google.android.gms.location.** { *; }
-dontwarn com.google.android.gms.location.**

# ===== AndroidX Security (EncryptedSharedPreferences) =====
-keep class androidx.security.crypto.** { *; }
-dontwarn androidx.security.crypto.**

# ===== Coil 图片加载 =====
-keep class coil.** { *; }
-dontwarn coil.**


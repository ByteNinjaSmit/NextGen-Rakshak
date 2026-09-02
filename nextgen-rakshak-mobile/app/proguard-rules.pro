# TensorFlow Lite / LiteRT and ML Kit rely on native/reflective loading.
-keep class org.tensorflow.lite.** { *; }
-keep class com.google.ai.edge.litert.** { *; }
-keep class com.google.mlkit.** { *; }

# Firestore/Firebase model classes are populated via reflection (toObject/get).
-keepattributes Signature,*Annotation*,EnclosingMethod,InnerClasses
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.firebase.**
-dontwarn com.google.android.gms.**

# Our own data models are read back from Firestore snapshots by field name.
-keep class com.rakshak.app.data.model.** { *; }
-keep class com.rakshak.app.data.local.PendingMatchEntity { *; }

# Room-generated code.
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.**

# Nearby Connections (offline mesh) payload models.
-keep class com.google.android.gms.nearby.** { *; }

# Credential Manager / Google ID token classes are parsed via reflection.
-keep class com.google.android.libraries.identity.googleid.** { *; }
-keep class androidx.credentials.** { *; }

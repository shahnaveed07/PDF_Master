# PDFBox-Android & BouncyCastle Keep Rules
-keep class com.tom_roush.pdfbox.** { *; }
-keep class com.tom_roush.fontbox.** { *; }
-keep class com.tom_roush.harmony.** { *; }
-dontwarn com.tom_roush.pdfbox.**
-dontwarn com.tom_roush.fontbox.**
-dontwarn com.tom_roush.harmony.**
-keep class org.bouncycastle.** { *; }
-dontwarn org.bouncycastle.**
-dontwarn org.apache.harmony.awt.**
-dontwarn java.awt.**
-dontwarn javax.imageio.**

# Room Database, DAO & Entity Keep Rules
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao class * { *; }
-keep class com.dsc.pdfmaster.data.database.** { *; }
-dontwarn androidx.room.paging.**

# General Keep Attributes
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes EnclosingMethod
-keepattributes InnerClasses

# Navigation Compose & kotlinx.serialization Type-Safe Routes Keep Rules
-keep,allowobfuscation,allowshrinking interface kotlinx.serialization.KSerializer
-keepclassmembers class com.dsc.pdfmaster.ui.navigation.** {
    public static ** INSTANCE;
    public static ** Companion;
    public kotlinx.serialization.KSerializer serializer(...);
}
-keep @kotlinx.serialization.Serializable class com.dsc.pdfmaster.ui.navigation.** { *; }

# Hilt / DI & ViewModels Keep Rules
-keep class com.dsc.pdfmaster.ui.viewmodel.** { *; }
-keep class com.dsc.pdfmaster.di.** { *; }


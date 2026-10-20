# PDFBox-Android Keep Rules
-keep class com.tom_roush.pdfbox.** { *; }
-dontwarn com.tom_roush.pdfbox.**
-dontwarn org.apache.harmony.awt.**
-dontwarn java.awt.**

# Room Keep Rules
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**

# General Keep Attributes
-keepattributes Signature
-keepattributes *Annotation*

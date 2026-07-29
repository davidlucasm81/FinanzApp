# Mantener todas las clases modelo de Firestore (POJOs) con su constructor
# sin argumentos, getters/setters y campos, para que toObject()/toObjects()
# sigan funcionando en release con minificación activada.
-keepclassmembers class com.finanzapp.app.data.model.** {
    <init>();
    <fields>;
    <methods>;
}
-keep class com.finanzapp.app.data.model.** { *; }

# Si usas @PropertyName, @Exclude, @ServerTimestamp, @IgnoreExtraProperties, etc.
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes InnerClasses
-keepattributes EnclosingMethod

# Rules to suppress warnings for missing classes (often from libraries like Apache POI)
-dontwarn aQute.bnd.annotation.baseline.BaselineIgnore
-dontwarn aQute.bnd.annotation.spi.ServiceConsumer
-dontwarn aQute.bnd.annotation.spi.ServiceProvider
-dontwarn edu.umd.cs.findbugs.annotations.Nullable
-dontwarn edu.umd.cs.findbugs.annotations.SuppressFBWarnings
-dontwarn java.awt.**
-dontwarn javax.xml.stream.**
-dontwarn net.sf.saxon.**
-dontwarn org.apache.batik.**
-dontwarn org.osgi.framework.**

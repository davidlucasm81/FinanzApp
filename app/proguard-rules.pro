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
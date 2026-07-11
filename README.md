# FinanzApp

Aplicación Android para gestión de finanzas familiares.

## Configuración de Firebase

Para que la aplicación funcione, necesitas configurar tu propio proyecto en Firebase:

1. Ve a [Firebase Console](https://console.firebase.google.com/).
2. Crea un nuevo proyecto llamado "FinanzApp".
3. Registra una nueva aplicación Android con el ID de paquete `com.finanzapp.app`.
4. Proporciona el SHA-1 de tu certificado de depuración (puedes obtenerlo ejecutando `./gradlew signingReport`).
5. Descarga el archivo `google-services.json`.
6. Coloca el archivo `google-services.json` en el directorio `app/` de este proyecto.

**Nota:** El archivo `google-services.json` está excluido del control de versiones por seguridad. Se proporciona `app/google-services.json.example` como referencia.

## Tecnologías

- Java
- Firebase Auth (Google)
- Cloud Firestore
- MVVM + LiveData
- Navigation Component
- ViewBinding
- MPAndroidChart

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.dagger.hilt.android")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.gmp.offline"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.gmp.offline"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0-fase4"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            // TODO Fase 4: reemplazar por la IP/dominio real del VPS antes de correr la app.
            // El backend hoy corre por HTTP plano (sin TLS), de ahí usesCleartextTraffic
            // en el manifest y network_security_config.xml. Cuando haya HTTPS, cambiar
            // a "https://..." acá y quitar el permiso de cleartext.
            buildConfigField("String", "API_BASE_URL", "\"http://69.169.102.33:3002/\"")
        }
        release {
            isMinifyEnabled = false
            buildConfigField("String", "API_BASE_URL", "\"http://69.169.102.33:3002/\"")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")

    // Lectura de orientación EXIF al comprimir fotos de montaje (Fase 6,
    // Paso 4) — corrige fotos "acostadas" al recomprimir con BitmapFactory.
    implementation("androidx.exifinterface:exifinterface:1.3.7")

    // Coil — carga de imágenes (foto local recién comprimida o la ya
    // subida al servidor) en Compose sin tener que manejar Bitmaps a mano
    // en la UI.
    implementation("io.coil-kt:coil-compose:2.6.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.4")
    implementation("androidx.activity:activity-compose:1.9.1")

    // Compose
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // Room — capa de datos local (núcleo de la Fase 4)
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // Hilt — inyección de dependencias
    implementation("com.google.dagger:hilt-android:2.51.1")
    ksp("com.google.dagger:hilt-compiler:2.51.1")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")

    // Navigation Compose — grafo de navegación real (Fase 6, reemplaza a
    // la pantalla única de debug de Fase 4/5).
    implementation("androidx.navigation:navigation-compose:2.7.7")

    // Retrofit — solo se usa acá para el cargador manual de un solo disparo
    // (OneShotSyncLoader) que prueba la capa Room. El cliente HTTP "de verdad"
    // integrado al outbox llega en la Fase 5.
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    // WorkManager — motor de sync real (Fase 5): SyncWorker corre por
    // conectividad + periódico + manual.
    implementation("androidx.work:work-runtime-ktx:2.9.1")
    implementation("androidx.hilt:hilt-work:1.2.0")
    ksp("androidx.hilt:hilt-compiler:1.2.0")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
}

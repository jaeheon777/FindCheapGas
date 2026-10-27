import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
}

// API 키는 local.properties에서 읽어 BuildConfig로 넘긴다. 값이 없으면 기본값을 쓴다.
val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) {
        file.inputStream().use { load(it) }
    }
}

fun localProperty(name: String, defaultValue: String = ""): String =
    localProperties.getProperty(name)?.trim() ?: defaultValue

fun quoted(value: String): String =
    "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

android {
    namespace = "com.example.findcheapgas"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.findcheapgas"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "OPINET_API_KEY", quoted(localProperty("OPINET_API_KEY")))
        buildConfigField("String", "KAKAO_NATIVE_APP_KEY", quoted(localProperty("KAKAO_NATIVE_APP_KEY")))
        buildConfigField("String", "KAKAO_REST_API_KEY", quoted(localProperty("KAKAO_REST_API_KEY")))
        // "false"라고 적은 경우에만 실제 API를 사용한다.
        val useMockData = !localProperty("USE_MOCK_DATA", "true").equals("false", ignoreCase = true)
        buildConfigField("boolean", "USE_MOCK_DATA", useMockData.toString())
    }

    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
}

dependencies {

    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)

    implementation(libs.kakao.map)
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)
    implementation(libs.okhttp.logging.interceptor)
    implementation(libs.room.runtime)
    annotationProcessor(libs.room.compiler)
    implementation(libs.play.services.location)
    implementation(libs.proj4j)
    implementation(libs.lifecycle.viewmodel)
    implementation(libs.lifecycle.livedata)

    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}
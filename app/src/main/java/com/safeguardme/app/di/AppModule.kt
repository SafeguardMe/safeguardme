// =============================================
// AppModule.kt - Updated DI Configuration
// =============================================

package com.safeguardme.app.di

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.safeguardme.app.BuildConfig
import com.safeguardme.app.auth.AuthRepository
import com.safeguardme.app.data.repositories.AIAssistantRepository
import com.safeguardme.app.data.repositories.ContactRepository
import com.safeguardme.app.data.repositories.EmergencyContactRepository
import com.safeguardme.app.data.repositories.FirebaseSafetyDataSource
import com.safeguardme.app.data.repositories.IncidentRepository
import com.safeguardme.app.data.repositories.SafetyDataSource
import com.safeguardme.app.data.repositories.SafetyEvidenceRepository
import com.safeguardme.app.data.repositories.SafetyRepository
import com.safeguardme.app.data.repositories.SafetyRepositoryImpl
import com.safeguardme.app.data.repositories.SettingsRepository
import com.safeguardme.app.data.repositories.StorageRepository
import com.safeguardme.app.data.repositories.UserRepository
import com.safeguardme.app.data.source.OpenAIService
import com.safeguardme.app.managers.EmergencyContactNotificationManager
import com.safeguardme.app.managers.EvidenceVault
import com.safeguardme.app.managers.PermissionManager
import com.safeguardme.app.managers.RiskAssessmentEngine
import com.safeguardme.app.managers.SafetyCoachManager
import com.safeguardme.app.managers.SafetyManager
import com.safeguardme.app.managers.VoiceDetectionManager
import com.safeguardme.app.services.EmergencySMSService
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.Provides
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.TimeUnit

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    // === FIREBASE CORE ===
    @Provides
    @Singleton
    fun provideFirebaseAuth(): FirebaseAuth = FirebaseAuth.getInstance()

    @Provides
    @Singleton
    fun provideFirebaseFirestore(): FirebaseFirestore = FirebaseFirestore.getInstance()

    @Provides
    @Singleton
    fun provideFirebaseStorage(): FirebaseStorage = FirebaseStorage.getInstance()

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        isLenient = true
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)

        if (BuildConfig.DEBUG) {
            val logging = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            }
            builder.addInterceptor(logging)
        }

        return builder.build()
    }

    // === REPOSITORIES ===
    @Provides
    @Singleton
    fun provideUserRepository(
        firestore: FirebaseFirestore,
        auth: FirebaseAuth
    ): UserRepository = UserRepository(firestore, auth)

    @Provides
    @Singleton
    fun provideAuthRepository(
        auth: FirebaseAuth,
        userRepository: UserRepository
    ): AuthRepository = AuthRepository(auth, userRepository)

    @Provides
    @Singleton
    fun provideEmergencyContactRepository(
        firestore: FirebaseFirestore,
        auth: FirebaseAuth,
        userRepository: UserRepository
    ): EmergencyContactRepository = EmergencyContactRepository(firestore, auth, userRepository)

    @Provides
    @Singleton
    fun provideContactRepository(
        firestore: FirebaseFirestore,
        auth: FirebaseAuth
    ): ContactRepository = ContactRepository(firestore, auth)

    @Provides
    @Singleton
    fun provideIncidentRepository(
        firestore: FirebaseFirestore,
        auth: FirebaseAuth
    ): IncidentRepository = IncidentRepository(firestore, auth)

    @Provides
    @Singleton
    fun provideStorageRepository(
        storage: FirebaseStorage,
        auth: FirebaseAuth
    ): StorageRepository = StorageRepository(storage, auth)

    @Provides
    @Singleton
    fun provideSettingsRepository(
        @ApplicationContext context: Context
    ): SettingsRepository = SettingsRepository(context)

    @Provides @Singleton
    fun provideSafetyDataSource(
        firestore: FirebaseFirestore,
        auth: FirebaseAuth
    ): SafetyDataSource =
        FirebaseSafetyDataSource(firestore, auth)          // ← concrete impl

    @Provides @Singleton
    fun provideSafetyRepository(
        dataSource: SafetyDataSource,
        userRepository: UserRepository
    ): SafetyRepository =
        SafetyRepositoryImpl(dataSource, userRepository)

    @Provides
    @Singleton
    fun provideOpenAIService(
        okHttpClient: OkHttpClient,
        json: Json
    ): OpenAIService = OpenAIService(okHttpClient, json)

    @Provides
    @Singleton
    fun provideAIAssistantRepository(
        openAIService: OpenAIService
    ): AIAssistantRepository = AIAssistantRepository(openAIService)

    @Provides
    @Singleton
    fun provideVoiceDetectionManager(
        @ApplicationContext  context: Context,
        userRepository: UserRepository,
        settingsRepository: SettingsRepository,
        permissionManager: PermissionManager,
        safetyManager: SafetyManager
    ): VoiceDetectionManager = VoiceDetectionManager(
        context, userRepository, settingsRepository, permissionManager, safetyManager
    )

    @Provides
    @Singleton
    fun provideSafetyManager(
        @ApplicationContext  context: Context,
        userRepository: UserRepository,
        safetyEvidenceRepository: SafetyEvidenceRepository,
        emergencyContactNotificationManager: EmergencyContactNotificationManager,
        permissionManager: PermissionManager
    ): SafetyManager = SafetyManager(
        context, userRepository, safetyEvidenceRepository,
        emergencyContactNotificationManager, permissionManager
    )

    @Provides
    @Singleton
    fun provideEvidenceVault(
        @ApplicationContext context: Context
    ): EvidenceVault = EvidenceVault(context)

    @Provides
    @Singleton
    fun provideRiskAssessmentEngine(
        aiAssistantRepository: AIAssistantRepository
    ): RiskAssessmentEngine = RiskAssessmentEngine(aiAssistantRepository)

    @Provides
    @Singleton
    fun provideSafetyCoachManager(
        aiAssistantRepository: AIAssistantRepository,
        safetyEvidenceRepository: SafetyEvidenceRepository,
        riskAssessmentEngine: RiskAssessmentEngine
    ): SafetyCoachManager = SafetyCoachManager(
        aiAssistantRepository,
        safetyEvidenceRepository,
        riskAssessmentEngine
    )

    @Provides
    @Singleton
    fun provideEmergencySMSService(
        @ApplicationContext context: Context,
        permissionManager: PermissionManager,
        emergencyContactRepository: EmergencyContactRepository
    ): EmergencySMSService {
        return EmergencySMSService(context, permissionManager, emergencyContactRepository)
    }
}

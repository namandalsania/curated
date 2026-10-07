package com.curated.app.core.data

import android.content.Context
import com.curated.app.BuildConfig
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.functions.Functions
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.serializer.KotlinXSerializer
import io.github.jan.supabase.storage.Storage
import kotlinx.serialization.json.Json

/** Lazily builds a single [SupabaseClient] shared across the app. */
object SupabaseProvider {

    @Volatile
    private var instance: SupabaseClient? = null

    fun client(context: Context): SupabaseClient {
        return instance ?: synchronized(this) {
            instance ?: buildClient().also { instance = it }
        }
    }

    private fun buildClient(): SupabaseClient {
        return createSupabaseClient(supabaseUrl = BuildConfig.SUPABASE_URL, supabaseKey = BuildConfig.SUPABASE_ANON_KEY) {
            defaultSerializer = KotlinXSerializer(Json { ignoreUnknownKeys = true })
            install(Postgrest)
            install(Auth)
            install(Storage)
            install(Realtime)
            install(Functions)
        }
    }
}

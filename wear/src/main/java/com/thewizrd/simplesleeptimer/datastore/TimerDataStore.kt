package com.thewizrd.simplesleeptimer.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.Serializer
import androidx.datastore.dataStore
import com.thewizrd.shared_resources.utils.JSONParser
import com.thewizrd.shared_resources.utils.stringToBytes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream

private object RemoteTimerStateCacheSerializer : Serializer<RemoteTimerStateCache> {
    override val defaultValue: RemoteTimerStateCache
        get() = RemoteTimerStateCache()

    override suspend fun readFrom(input: InputStream): RemoteTimerStateCache {
        return JSONParser.deserializer(input, RemoteTimerStateCache::class.java) ?: defaultValue
    }

    override suspend fun writeTo(t: RemoteTimerStateCache, output: OutputStream) {
        withContext(Dispatchers.IO) {
            output.write(
                JSONParser.serializer(t, RemoteTimerStateCache::class.java).stringToBytes()
            )
        }
    }
}

val Context.remoteTimerDataStore: DataStore<RemoteTimerStateCache> by dataStore(
    fileName = "remote_timer_cache.json",
    serializer = RemoteTimerStateCacheSerializer
)
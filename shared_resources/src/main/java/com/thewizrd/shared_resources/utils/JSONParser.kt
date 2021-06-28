package com.thewizrd.shared_resources.utils

import android.util.Log
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.stream.JsonReader
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.lang.reflect.Type

object JSONParser {
    private const val TAG = "JSONParser"

    private val gson: Gson = GsonBuilder()
        .serializeNulls()
        .create()

    fun <T> deserializer(response: String?, type: Type?): T? {
        var `object`: T? = null

        try {
            `object` = gson.fromJson(response, type)
        } catch (ex: Exception) {
            Log.e(TAG, "Error", ex)
        }

        return `object`
    }

    fun <T> deserializer(response: String?, obj: Class<T>?): T? {
        var `object`: T? = null

        try {
            `object` = gson.fromJson(response, obj)
        } catch (ex: Exception) {
            Log.e(TAG, "Error", ex)
        }

        return `object`
    }

    fun <T> deserializer(stream: InputStream, type: Type?): T? {
        var `object`: T? = null
        var sReader: InputStreamReader? = null
        var reader: JsonReader? = null

        try {
            sReader = InputStreamReader(stream)
            reader = JsonReader(sReader)

            `object` = gson.fromJson(reader, type)
        } catch (ex: Exception) {
            Log.e(TAG, "Error", ex)
        } finally {
            try {
                reader?.close()
            } catch (e: IOException) {
                //e.printStackTrace();
            }
            try {
                sReader?.close()
            } catch (e: IOException) {
                //e.printStackTrace();
            }
        }

        return `object`
    }

    @JvmName("serializerOrNull")
    fun serializer(`object`: Any?, type: Type): String? {
        if (`object` == null) return null
        return serializer(`object`, type)
    }

    fun serializer(`object`: Any, type: Type): String {
        return gson.toJson(`object`, type)
    }
}
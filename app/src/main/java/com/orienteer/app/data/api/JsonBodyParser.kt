package com.orienteer.app.data.api

import com.google.gson.Gson
import okhttp3.ResponseBody

/** Parse Retrofit [ResponseBody] with an explicit class (R8-safe; no ParameterizedType). */
internal fun <T> ResponseBody.parseJson(gson: Gson, clazz: Class<T>): T =
    use { body -> gson.fromJson(body.charStream(), clazz) }

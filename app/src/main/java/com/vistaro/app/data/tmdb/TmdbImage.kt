package com.vistaro.app.data.tmdb

fun tmdbImageUrl(path: String?, size: String = "w342"): String? =
    path?.takeIf { it.startsWith('/') }?.let { "https://image.tmdb.org/t/p/$size$it" }

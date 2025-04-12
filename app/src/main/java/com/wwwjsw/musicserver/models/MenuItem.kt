package com.wwwjsw.musicserver.models

data class MenuItem(
    val id: Int,
    val name: String,
    val imageVector: Int,
    val filter: FilterType
)

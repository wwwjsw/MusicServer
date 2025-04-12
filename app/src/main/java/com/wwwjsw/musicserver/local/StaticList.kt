package com.wwwjsw.musicserver.local

import com.wwwjsw.musicserver.R
import com.wwwjsw.musicserver.models.FilterType
import com.wwwjsw.musicserver.models.MenuItem


object StaticLists {
    val menuItems = listOf(
        MenuItem(
            1,
            "All Songs",
            imageVector = R.drawable.twotone_play_circle_24,
            FilterType.ALL
        ),
        MenuItem(
            2,
            "Albums",
            imageVector =  R.drawable.baseline_theater_comedy_24,
            FilterType.ALBUMS
        )
    )
}
package com.example.blockhome

import android.content.ComponentName
import android.graphics.drawable.Drawable
import android.os.UserHandle

data class AppEntry(
    val label: String,
    val component: ComponentName,
    val user: UserHandle,
    val icon: Drawable
)

package com.happycola233.bilitools.core

import android.content.Context
import com.happycola233.bilitools.BiliToolsApp

val Context.appContainer: AppContainer
    get() = (applicationContext as BiliToolsApp).container

package com.happycola233.bilitools

import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.SQLiteMode
import org.robolectric.config.ConfigurationRegistry
import org.robolectric.nativeruntime.DefaultNativeRuntimeLoader
import org.robolectric.pluginapi.TestEnvironmentLifecyclePlugin

/**
 * 在每个 Robolectric 沙箱初始化、应用启动之前，于测试主线程加载原生运行时。
 *
 * Robolectric 4.16 只在 API 35+ 预先加载原生运行时，更低版本要等首次用到原生 SQLite 或图形时才加载。
 * 应用启动时的后台协程（如相册可见性同步）会访问数据库，可能在测试结束后才在后台线程触发这次加载；
 * 加载时会用 FileSystems.newFileSystem 打开全 JVM 共享的字体 JAR，若与下一个测试类的沙箱同时打开，
 * 后者会抛出 FileSystemAlreadyExistsException。提前在主线程加载后，后台线程不会再触发加载。
 *
 * 上游已修复（robolectric/robolectric@a52004a），升级到包含该修复的 Robolectric 后，
 * 可删除本类及 META-INF/services 下的注册文件。
 */
class NativeRuntimePreloader : TestEnvironmentLifecyclePlugin {
    override fun onSetupApplicationState() {
        // 与 Robolectric 自身判断是否需要原生运行时的条件一致
        val usesNativeRuntime =
            ConfigurationRegistry.get(GraphicsMode.Mode::class.java) == GraphicsMode.Mode.NATIVE ||
                ConfigurationRegistry.get(SQLiteMode.Mode::class.java) == SQLiteMode.Mode.NATIVE
        if (usesNativeRuntime) DefaultNativeRuntimeLoader.injectAndLoad()
    }
}

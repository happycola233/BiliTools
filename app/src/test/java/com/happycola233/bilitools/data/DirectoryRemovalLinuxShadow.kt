package com.happycola233.bilitools.data

import android.system.ErrnoException
import android.system.OsConstants
import java.nio.file.DirectoryNotEmptyException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.junit.rules.ExternalResource
import org.robolectric.shadows.ShadowLinux
import org.robolectric.util.ReflectionHelpers

/** Robolectric 未实现 Linux.remove；用真实文件系统补齐目录删除与末尾 / 的语义。 */
@Implements(className = "libcore.io.Linux", isInAndroidSdk = false)
class DirectoryRemovalLinuxShadow : ShadowLinux() {
    @Implementation
    fun remove(path: String) {
        val target = Paths.get(path)
        beforeRemove?.also { beforeRemove = null }?.invoke(target)
        if (path.endsWith('/') && !Files.isDirectory(target)) {
            throw ErrnoException("remove", OsConstants.ENOTDIR)
        }
        try {
            Files.delete(target)
        } catch (_: DirectoryNotEmptyException) {
            throw ErrnoException("remove", OsConstants.ENOTEMPTY)
        }
    }

    companion object {
        var beforeRemove: ((Path) -> Unit)? = null
    }

    /** Android 10 的 Libcore 单例会跨测试保留旧 shadow；每例重新建立并在结束时归还。 */
    class Fixture : ExternalResource() {
        private lateinit var originalOs: Any
        private val libcore get() = Class.forName("libcore.io.Libcore")

        override fun before() {
            originalOs = ReflectionHelpers.getStaticField(libcore, "os")
            val linux = Class.forName("libcore.io.Linux").getDeclaredConstructor()
                .apply { isAccessible = true }.newInstance()
            ReflectionHelpers.setStaticField(libcore, "os", linux)
        }

        override fun after() {
            beforeRemove = null
            ReflectionHelpers.setStaticField(libcore, "os", originalOs)
        }
    }
}

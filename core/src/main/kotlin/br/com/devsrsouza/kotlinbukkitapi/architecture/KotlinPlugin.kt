package br.com.devsrsouza.kotlinbukkitapi.architecture

import br.com.devsrsouza.kotlinbukkitapi.architecture.lifecycle.*
import org.bukkit.plugin.java.JavaPlugin
import java.util.concurrent.ConcurrentSkipListSet

open class KotlinPlugin : JavaPlugin() {

    open fun onPluginLoad() {}
    open fun onPluginEnable() {}
    open fun onPluginDisable() {}
    open fun onConfigReload() {}

    /**
     * Register the lifecycle listener returned by the [block] lambda with the given [priority].
     *
     * **Priority Order**: High priority loads first and disable lastly
     */
    inline fun <P : KotlinPlugin, T : LifecycleListener<P>> lifecycle(
            priority: Int = 1,
            block: () -> T
    ): T = block().also {
        registerKotlinPluginLifecycle(priority, it as LifecycleListener<KotlinPlugin>)
    }

    /**
     * Register a lifecycle listener with the given [priority].
     */
    fun registerKotlinPluginLifecycle(
            priority: Int = 1,
            listener: PluginLifecycleListener
    ) {
        _lifecycleListeners.add(
            Lifecycle(
                priority,
                listener
            )
        )
    }

    // implementation stuff, ignore...
    private val _lifecycleListeners = ConcurrentSkipListSet<Lifecycle>()
    val lifecycleListeners: Set<Lifecycle> = _lifecycleListeners


    final override fun onLoad() {
        onPluginLoad()

        for(lifecycle in _lifecycleListeners)
            lifecycle.listener(LifecycleEvent.LOAD)
    }

    final override fun onEnable() {
        onPluginEnable()

        for(lifecycle in _lifecycleListeners)
            lifecycle.listener(LifecycleEvent.ENABLE)
    }

    final override fun onDisable() {
        onPluginDisable()

        // reversing lifecycles for execute first the low priority ones
        val reversedLifecyle = _lifecycleListeners.descendingSet()

        for(lifecycle in reversedLifecyle)
            lifecycle.listener(LifecycleEvent.DISABLE)
    }

    final override fun reloadConfig() {
        super.reloadConfig()

        someConfigReloaded()
    }

    fun someConfigReloaded() {
        onConfigReload()

        for(lifecycle in _lifecycleListeners)
            lifecycle.listener(LifecycleEvent.CONFIG_RELOAD)
    }

}
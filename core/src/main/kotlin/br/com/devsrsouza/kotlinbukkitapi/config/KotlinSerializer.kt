package br.com.devsrsouza.kotlinbukkitapi.config

import kotlin.reflect.KClass
import kotlin.reflect.full.createInstance
import kotlin.reflect.jvm.isAccessible

object KotlinSerializer {

    fun instanceToMap(instance: Any, adapter: PropertySaveAdapter): Map<String, Any> {
        val map = mutableMapOf<String, Any>()

        val clazz = instance::class
        val properties = publicMutablePropertiesFrom(clazz)
        loop@ for (prop in properties) {
            fun put(any: Any, key: String = prop.name) {
                map.put(key, any)
            }

            prop.isAccessible = true

            //val obj = prop.get(instance)
            val (obj, type) = adapter(instance, prop, prop.returnType, prop.get(instance))
            when {
                type.isPrimitive -> put(obj)
                type.isList && type.isFirstGenericPrimitive -> put(obj)
                type.isList -> {
                    val list = obj as List<Any>
                    val map = list.withIndex().associate { (key, value) ->
                        key.toString() to instanceToMap(value, adapter)
                    }
                    put(map)
                }
                type.isMap && type.isFirstGenericString && type.isSecondGenericPrimitive -> put(obj)
                type.isMap && type.isFirstGenericString -> {
                    val map = obj as Map<String, Any>

                    val newMap = mutableMapOf<String, Any>()
                    for ((key, value) in map) {
                        newMap.put(key, instanceToMap(value, adapter))
                    }

                    put(newMap)
                }
                type.isEnum -> put((obj as Enum<*>).name)
                else -> put(instanceToMap(obj, adapter))
            }
        }

        return map
    }

    fun <T : Any> mapToInstance(type: KClass<T>, map: Map<String, Any>, adapter: PropertyLoadAdapter): T {
        val instance = type.objectInstance ?: type.createInstance()

        val properties = publicMutablePropertiesFrom(type)
        loop@ for (prop in properties) {
            val fromMap = map.get(prop.name) ?: continue@loop
            val type = prop.returnType

            prop.isAccessible = true

            val obj = adapter(
                    instance,
                    prop,
                    if (fromMap is Number) fixNumberType(type, fromMap) else fromMap
            )

            fun set(any: Any) {
                val setter = prop.setter.apply { isAccessible = true }
                setter.call(instance, any)
            }

            when {
                type.isPrimitive -> set(obj)
                type.isList && type.isFirstGenericPrimitive -> set(obj)
                type.isList -> {
                    val type = type.firstGenericType?.kclass ?: continue@loop
                    val list = obj as Map<String, Any>

                    set(list.values.map { mapToInstance(type, it as Map<String, Any>, adapter) })
                }
                type.isMap && type.isFirstGenericString && type.isSecondGenericPrimitive -> set(obj)
                type.isMap && type.isFirstGenericString -> {
                    val type = type.firstGenericType?.kclass ?: continue@loop
                    val map = obj as Map<String, Any>

                    set(map.mapValues { mapToInstance(type, it as Map<String, Any>, adapter) })
                }
                type.classifier == obj::class -> set(obj)
                type.isEnum -> {
                    val enumClass = prop.returnType.classifier as KClass<Enum<*>>
                    val value = (obj as? String)?.let { getEnumValueByName(enumClass, it) }
                            ?: continue@loop

                    set(value)
                }
                else -> {
                    val type = prop.returnType.classifier as? KClass<*> ?: continue@loop
                    set(mapToInstance(type, obj as Map<String, Any>, adapter))
                }
            }
        }

        return instance
    }
}
package dagger.hilt

import javax.inject.Qualifier
import javax.inject.Scope
import kotlin.reflect.KClass

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class InstallIn(vararg val value: KClass<*>)

@Scope
@Retention(AnnotationRetention.RUNTIME)
annotation class SingletonComponentScope

interface SingletonComponent

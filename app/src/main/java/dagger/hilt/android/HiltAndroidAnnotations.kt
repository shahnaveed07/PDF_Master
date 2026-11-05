package dagger.hilt.android

import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Qualifier

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class HiltAndroidApp

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class AndroidEntryPoint

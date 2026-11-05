package com.dsc.pdfmaster.di

import android.app.Application
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dsc.pdfmaster.data.database.PdfDatabase
import com.dsc.pdfmaster.data.database.RecentPdfDao
import com.dsc.pdfmaster.data.repository.PdfRepository
import com.dsc.pdfmaster.pdfengine.PdfEngine
import com.dsc.pdfmaster.pdfengine.PdfPageBitmapCache
import com.dsc.pdfmaster.ui.PdfMasterViewModel
import com.dsc.pdfmaster.ui.viewmodel.AnnotatePdfViewModel
import com.dsc.pdfmaster.ui.viewmodel.CompressViewModel
import com.dsc.pdfmaster.ui.viewmodel.ConvertViewModel
import com.dsc.pdfmaster.ui.viewmodel.DocumentScannerViewModel
import com.dsc.pdfmaster.ui.viewmodel.MergeViewModel
import com.dsc.pdfmaster.ui.viewmodel.OcrViewModel
import com.dsc.pdfmaster.ui.viewmodel.PagesViewModel
import com.dsc.pdfmaster.ui.viewmodel.RecentViewModel
import com.dsc.pdfmaster.ui.viewmodel.SecurityViewModel
import com.dsc.pdfmaster.ui.viewmodel.SignPdfViewModel
import com.dsc.pdfmaster.ui.viewmodel.SplitViewModel
import com.dsc.pdfmaster.ui.viewmodel.ViewerViewModel
import com.dsc.pdfmaster.ui.viewmodel.WatermarkPdfViewModel
import com.dsc.pdfmaster.work.PdfWorkManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.SingletonComponent
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Volatile
    private var appContext: Application? = null

    @Volatile
    private var pdfDatabaseInstance: PdfDatabase? = null

    @Volatile
    private var recentPdfDaoInstance: RecentPdfDao? = null

    @Volatile
    private var pdfRepositoryInstance: PdfRepository? = null

    @Volatile
    private var pdfWorkManagerInstance: PdfWorkManager? = null

    @Volatile
    private var bitmapCacheInstance: PdfPageBitmapCache? = null

    fun initialize(application: Application) {
        synchronized(this) {
            appContext = application
            val db = providePdfDatabase(application)
            val dao = provideRecentPdfDao(db)
            val repo = providePdfRepository(application, dao)
            providePdfWorkManager(application, repo)
            providePdfPageBitmapCache()
        }
    }

    fun requireApplication(fallbackContext: Context? = null): Application {
        appContext?.let { return it }
        val resolved = fallbackContext?.applicationContext as? Application
            ?: throw IllegalStateException("Application context not initialized")
        initialize(resolved)
        return resolved
    }

    @Provides
    @Singleton
    fun providePdfDatabase(@ApplicationContext context: Context): PdfDatabase {
        return pdfDatabaseInstance ?: synchronized(this) {
            pdfDatabaseInstance ?: PdfDatabase.getDatabase(context.applicationContext).also {
                pdfDatabaseInstance = it
            }
        }
    }

    @Provides
    @Singleton
    fun provideRecentPdfDao(database: PdfDatabase): RecentPdfDao {
        return recentPdfDaoInstance ?: synchronized(this) {
            recentPdfDaoInstance ?: database.recentPdfDao().also {
                recentPdfDaoInstance = it
            }
        }
    }

    @Provides
    @Singleton
    fun providePdfRepository(
        @ApplicationContext context: Context,
        dao: RecentPdfDao
    ): PdfRepository {
        return pdfRepositoryInstance ?: synchronized(this) {
            pdfRepositoryInstance ?: PdfRepository(context.applicationContext, dao).also {
                pdfRepositoryInstance = it
            }
        }
    }

    @Provides
    @Singleton
    fun providePdfEngine(): PdfEngine = PdfEngine

    @Provides
    @Singleton
    fun providePdfPageBitmapCache(): PdfPageBitmapCache {
        return bitmapCacheInstance ?: synchronized(this) {
            bitmapCacheInstance ?: PdfPageBitmapCache.sharedInstance.also {
                bitmapCacheInstance = it
            }
        }
    }

    @Provides
    @Singleton
    fun providePdfWorkManager(
        @ApplicationContext context: Context,
        repository: PdfRepository
    ): PdfWorkManager {
        return pdfWorkManagerInstance ?: synchronized(this) {
            pdfWorkManagerInstance ?: PdfWorkManager(context.applicationContext, repository).also {
                pdfWorkManagerInstance = it
                PdfWorkManager.sharedInstance = it
            }
        }
    }

    fun createViewModelFactory(context: Context): ViewModelProvider.Factory {
        val app = requireApplication(context)
        val db = providePdfDatabase(app)
        val dao = provideRecentPdfDao(db)
        val repository = providePdfRepository(app, dao)
        val workManager = providePdfWorkManager(app, repository)
        val bitmapCache = providePdfPageBitmapCache()

        return object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return when {
                    modelClass.isAssignableFrom(PdfMasterViewModel::class.java) ->
                        PdfMasterViewModel(app, repository, workManager, bitmapCache) as T
                    modelClass.isAssignableFrom(MergeViewModel::class.java) ->
                        MergeViewModel(repository, workManager) as T
                    modelClass.isAssignableFrom(SplitViewModel::class.java) ->
                        SplitViewModel(repository) as T
                    modelClass.isAssignableFrom(CompressViewModel::class.java) ->
                        CompressViewModel(repository, workManager) as T
                    modelClass.isAssignableFrom(ConvertViewModel::class.java) ->
                        ConvertViewModel(app, repository, workManager) as T
                    modelClass.isAssignableFrom(PagesViewModel::class.java) ->
                        PagesViewModel(app, repository) as T
                    modelClass.isAssignableFrom(SecurityViewModel::class.java) ->
                        SecurityViewModel(app, repository) as T
                    modelClass.isAssignableFrom(ViewerViewModel::class.java) ->
                        ViewerViewModel(app, repository, bitmapCache) as T
                    modelClass.isAssignableFrom(RecentViewModel::class.java) ->
                        RecentViewModel(repository) as T
                    modelClass.isAssignableFrom(SignPdfViewModel::class.java) ->
                        SignPdfViewModel(app, repository) as T
                    modelClass.isAssignableFrom(AnnotatePdfViewModel::class.java) ->
                        AnnotatePdfViewModel(app, repository) as T
                    modelClass.isAssignableFrom(WatermarkPdfViewModel::class.java) ->
                        WatermarkPdfViewModel(repository) as T
                    modelClass.isAssignableFrom(OcrViewModel::class.java) ->
                        OcrViewModel(repository) as T
                    modelClass.isAssignableFrom(DocumentScannerViewModel::class.java) ->
                        DocumentScannerViewModel(repository) as T
                    else -> throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
                }
            }
        }
    }
}

@Composable
inline fun <reified VM : ViewModel> hiltViewModel(
    viewModelStoreOwner: ViewModelStoreOwner = checkNotNull(LocalViewModelStoreOwner.current) {
        "No ViewModelStoreOwner was provided via LocalViewModelStoreOwner"
    }
): VM {
    val context = LocalContext.current
    return viewModel(
        viewModelStoreOwner = viewModelStoreOwner,
        factory = AppModule.createViewModelFactory(context)
    )
}

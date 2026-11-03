package app.tropereader.di

import android.content.Context
import androidx.room.Room
import app.tropereader.data.CachedPageRepository
import app.tropereader.data.DataStoreSettingsRepository
import app.tropereader.data.RoomLibraryRepository
import app.tropereader.data.RoomReadingProgressRepository
import app.tropereader.data.db.CachedPageDao
import app.tropereader.data.db.LibraryDao
import app.tropereader.data.db.ProgressDao
import app.tropereader.data.db.TropeReaderDatabase
import app.tropereader.data.fetch.PageFetcher
import app.tropereader.data.fetch.WebViewPageFetcher
import app.tropereader.domain.LibraryRepository
import app.tropereader.domain.PageRepository
import app.tropereader.domain.ReadingProgressRepository
import app.tropereader.domain.SettingsRepository
import app.tropereader.parser.PageParser
import app.tropereader.parser.TvTropesPageParser
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DataModule {
    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): TropeReaderDatabase =
        // No fallbackToDestructiveMigration, ever (F11.2).
        Room.databaseBuilder(context, TropeReaderDatabase::class.java, TropeReaderDatabase.NAME).build()

    @Provides
    fun progressDao(db: TropeReaderDatabase): ProgressDao = db.progressDao()

    @Provides
    fun libraryDao(db: TropeReaderDatabase): LibraryDao = db.libraryDao()

    @Provides
    fun cachedPageDao(db: TropeReaderDatabase): CachedPageDao = db.cachedPageDao()

    @Provides
    fun pageParser(): PageParser = TvTropesPageParser()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class BindingsModule {
    @Binds
    abstract fun progress(impl: RoomReadingProgressRepository): ReadingProgressRepository

    @Binds
    abstract fun library(impl: RoomLibraryRepository): LibraryRepository

    @Binds
    abstract fun pages(impl: CachedPageRepository): PageRepository

    @Binds
    abstract fun fetcher(impl: WebViewPageFetcher): PageFetcher

    @Binds
    abstract fun settings(impl: DataStoreSettingsRepository): SettingsRepository
}

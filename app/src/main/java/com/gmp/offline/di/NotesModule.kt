package com.gmp.offline.di

import android.content.Context
import androidx.room.Room
import com.gmp.offline.data.notes.NotesDao
import com.gmp.offline.data.notes.NotesDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NotesModule {
    @Provides
    @Singleton
    fun provideNotesDatabase(@ApplicationContext context: Context): NotesDatabase =
        Room.databaseBuilder(context, NotesDatabase::class.java, "gmp-notes.db").build()

    @Provides
    fun provideNotesDao(database: NotesDatabase): NotesDao = database.notesDao()
}

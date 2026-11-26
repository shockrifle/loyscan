package com.bdaniel.loyscan.data.repository

import com.bdaniel.loyscan.domain.LogoTreatment
import com.bdaniel.loyscan.domain.Store
import kotlinx.coroutines.flow.Flow

/** Stores as domain models. ViewModels depend on this; tests swap in a fake. */
interface StoreRepository {

    fun getAll(): Flow<List<Store>>

    /** Saves a new store, e.g. a custom one the user created (#6). */
    suspend fun add(store: Store)

    /**
     * Saves an edit of a custom store (#53): its name, colour, barcode format for typed numbers and `updatedAt`. Does
     * nothing for a catalogue store or an unknown id.
     */
    suspend fun update(store: Store)

    /**
     * Sets any store's logo (#151), catalogue or custom: a file name in the photos' storage
     * ([com.bdaniel.loyscan.data.photos.CardPhotos.cropLogo]); `null` removes it. The file of the logo it replaces is
     * deleted, and [logo]'s own when there is no such store. A new logo, or none, is drawn As is (#240).
     */
    suspend fun setLogo(storeId: String, logo: String?)

    /** Sets how any store's logo is drawn (#240). Does nothing for an unknown id. */
    suspend fun setLogoTreatment(storeId: String, treatment: LogoTreatment)

    /** Deletes a custom store and its cards with their photos (#53, #10), and its logo (#151). Does nothing for a catalogue store or an unknown id. */
    suspend fun delete(id: String)
}

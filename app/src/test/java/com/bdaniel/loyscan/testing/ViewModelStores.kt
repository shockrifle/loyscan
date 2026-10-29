package com.bdaniel.loyscan.testing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory

/** Creates a ViewModel kept in this store, so [ViewModelStore.clear] clears it, as leaving its screen does. */
inline fun <reified VM : ViewModel> ViewModelStore.keep(noinline create: () -> VM): VM =
    ViewModelProvider.create(this, viewModelFactory { initializer { create() } })[VM::class]

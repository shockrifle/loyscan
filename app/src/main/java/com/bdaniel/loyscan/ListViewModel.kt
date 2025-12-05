package com.bdaniel.loyscan

import androidx.lifecycle.ViewModel
import com.bdaniel.loyscan.domain.Brand
import com.bdaniel.loyscan.domain.LoyaltyCard
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class ListViewModel @Inject constructor() : ViewModel() {

    private val _cards = MutableStateFlow<List<LoyaltyCard>>(emptyList())
    val cards: StateFlow<List<LoyaltyCard>> = _cards

    init {
        // Populate with dummy data
        _cards.value = listOf(
            LoyaltyCard(1, "My Favorite Store", Brand(1, "FavoriteBrand", "#FF0000", ""), "123456789", "QR_CODE"),
            LoyaltyCard(2, "Coffee Shop", Brand(2, "CoffeeBrand", "#00FF00", ""), "987654321", "CODE_128"),
            LoyaltyCard(3, "Grocery", Brand(3, "GroceryBrand", "#0000FF", ""), "555555555", "EAN_13"),
            LoyaltyCard(4, "Bookstore", Brand(4, "BookBrand", "#FFFF00", ""), "111222333", "UPC_A"),
            LoyaltyCard(5, "Gym Membership", Brand(5, "GymBrand", "#00FFFF", ""), "444444444", "AZTEC"),
            LoyaltyCard(6, "Electronics", Brand(6, "TechBrand", "#FF00FF", ""), "666777888", "DATA_MATRIX"),
            LoyaltyCard(7, "Hardware Store", Brand(7, "HardwareBrand", "#C0C0C0", ""), "999888777", "PDF_417"),
            LoyaltyCard(8, "Pet Supplies", Brand(8, "PetBrand", "#800000", ""), "123123123", "QR_CODE"),
            LoyaltyCard(9, "Pharmacy", Brand(9, "HealthBrand", "#808000", ""), "456456456", "CODE_128"),
            LoyaltyCard(10, "Restaurant", Brand(10, "FoodBrand", "#008080", ""), "789789789", "EAN_13")
        )
    }
}
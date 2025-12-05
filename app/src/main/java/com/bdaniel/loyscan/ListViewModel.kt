package com.bdaniel.loyscan

import androidx.lifecycle.ViewModel
import com.bdaniel.loyscan.domain.LoyaltyCard
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class ListViewModel : ViewModel() {

    private val _cards = MutableStateFlow<List<LoyaltyCard>>(emptyList())
    val cards: StateFlow<List<LoyaltyCard>> = _cards

    init {
        // Populate with dummy data
        _cards.value = listOf(
                LoyaltyCard(1, "My Favorite Store", "FavoriteBrand", "123456789", "QR_CODE"),
                LoyaltyCard(2, "Coffee Shop", "CoffeeBrand", "987654321", "CODE_128"),
                LoyaltyCard(3, "Grocery", "GroceryBrand", "555555555", "EAN_13"),
                LoyaltyCard(4, "Bookstore", "BookBrand", "111222333", "UPC_A"),
                LoyaltyCard(5, "Gym Membership", "GymBrand", "444444444", "AZTEC"),
                LoyaltyCard(6, "Electronics", "TechBrand", "666777888", "DATA_MATRIX"),
                LoyaltyCard(7, "Hardware Store", "HardwareBrand", "999888777", "PDF_417"),
                LoyaltyCard(8, "Pet Supplies", "PetBrand", "123123123", "QR_CODE"),
                LoyaltyCard(9, "Pharmacy", "HealthBrand", "456456456", "CODE_128"),
                LoyaltyCard(10, "Restaurant", "FoodBrand", "789789789", "EAN_13")
        )
    }
}
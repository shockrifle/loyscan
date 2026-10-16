package com.bdaniel.loyscan.ui.theme.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

object Icons {
    object Default {
        val Settings: ImageVector
            get() {
                if (_Settings != null) {
                    return _Settings!!
                }
                _Settings = ImageVector.Builder(
                        name = "Settings",
                        defaultWidth = 24.dp,
                        defaultHeight = 24.dp,
                        viewportWidth = 960f,
                        viewportHeight = 960f
                ).apply {
                    path(fill = SolidColor(Color.Black)) {
                        moveToRelative(370f, 880f)
                        lineToRelative(-16f, -128f)
                        quadToRelative(-13f, -5f, -24.5f, -12f)
                        reflectiveQuadTo(307f, 725f)
                        lineToRelative(-119f, 50f)
                        lineTo(78f, 585f)
                        lineToRelative(103f, -78f)
                        quadToRelative(-1f, -7f, -1f, -13.5f)
                        verticalLineToRelative(-27f)
                        quadToRelative(0f, -6.5f, 1f, -13.5f)
                        lineTo(78f, 375f)
                        lineToRelative(110f, -190f)
                        lineToRelative(119f, 50f)
                        quadToRelative(11f, -8f, 23f, -15f)
                        reflectiveQuadToRelative(24f, -12f)
                        lineToRelative(16f, -128f)
                        horizontalLineToRelative(220f)
                        lineToRelative(16f, 128f)
                        quadToRelative(13f, 5f, 24.5f, 12f)
                        reflectiveQuadToRelative(22.5f, 15f)
                        lineToRelative(119f, -50f)
                        lineToRelative(110f, 190f)
                        lineToRelative(-103f, 78f)
                        quadToRelative(1f, 7f, 1f, 13.5f)
                        verticalLineToRelative(27f)
                        quadToRelative(0f, 6.5f, -2f, 13.5f)
                        lineToRelative(103f, 78f)
                        lineToRelative(-110f, 190f)
                        lineToRelative(-118f, -50f)
                        quadToRelative(-11f, 8f, -23f, 15f)
                        reflectiveQuadToRelative(-24f, 12f)
                        lineTo(590f, 880f)
                        lineTo(370f, 880f)
                        close()
                        moveTo(440f, 800f)
                        horizontalLineToRelative(79f)
                        lineToRelative(14f, -106f)
                        quadToRelative(31f, -8f, 57.5f, -23.5f)
                        reflectiveQuadTo(639f, 633f)
                        lineToRelative(99f, 41f)
                        lineToRelative(39f, -68f)
                        lineToRelative(-86f, -65f)
                        quadToRelative(5f, -14f, 7f, -29.5f)
                        reflectiveQuadToRelative(2f, -31.5f)
                        quadToRelative(0f, -16f, -2f, -31.5f)
                        reflectiveQuadToRelative(-7f, -29.5f)
                        lineToRelative(86f, -65f)
                        lineToRelative(-39f, -68f)
                        lineToRelative(-99f, 42f)
                        quadToRelative(-22f, -23f, -48.5f, -38.5f)
                        reflectiveQuadTo(533f, 266f)
                        lineToRelative(-13f, -106f)
                        horizontalLineToRelative(-79f)
                        lineToRelative(-14f, 106f)
                        quadToRelative(-31f, 8f, -57.5f, 23.5f)
                        reflectiveQuadTo(321f, 327f)
                        lineToRelative(-99f, -41f)
                        lineToRelative(-39f, 68f)
                        lineToRelative(86f, 64f)
                        quadToRelative(-5f, 15f, -7f, 30f)
                        reflectiveQuadToRelative(-2f, 32f)
                        quadToRelative(0f, 16f, 2f, 31f)
                        reflectiveQuadToRelative(7f, 30f)
                        lineToRelative(-86f, 65f)
                        lineToRelative(39f, 68f)
                        lineToRelative(99f, -42f)
                        quadToRelative(22f, 23f, 48.5f, 38.5f)
                        reflectiveQuadTo(427f, 694f)
                        lineToRelative(13f, 106f)
                        close()
                        moveTo(482f, 620f)
                        quadToRelative(58f, 0f, 99f, -41f)
                        reflectiveQuadToRelative(41f, -99f)
                        quadToRelative(0f, -58f, -41f, -99f)
                        reflectiveQuadToRelative(-99f, -41f)
                        quadToRelative(-59f, 0f, -99.5f, 41f)
                        reflectiveQuadTo(342f, 480f)
                        quadToRelative(0f, 58f, 40.5f, 99f)
                        reflectiveQuadToRelative(99.5f, 41f)
                        close()
                        moveTo(480f, 480f)
                        close()
                    }
                }.build()

                return _Settings!!
            }

        private var _Settings: ImageVector? = null

        val ArrowBack: ImageVector
            get() {
                if (_ArrowBack != null) {
                    return _ArrowBack!!
                }
                _ArrowBack = ImageVector.Builder(
                        name = "ArrowBack",
                        defaultWidth = 24.dp,
                        defaultHeight = 24.dp,
                        viewportWidth = 960f,
                        viewportHeight = 960f
                ).apply {
                    path(fill = SolidColor(Color.Black)) {
                        moveToRelative(313f, 520f)
                        lineToRelative(224f, 224f)
                        lineToRelative(-57f, 56f)
                        lineToRelative(-320f, -320f)
                        lineToRelative(320f, -320f)
                        lineToRelative(57f, 56f)
                        lineToRelative(-224f, 224f)
                        horizontalLineToRelative(487f)
                        verticalLineToRelative(80f)
                        lineTo(313f, 520f)
                        close()
                    }
                }.build()

                return _ArrowBack!!
            }

        @Suppress("ObjectPropertyName")
        private var _ArrowBack: ImageVector? = null

        val Edit: ImageVector
            get() {
                if (_Edit != null) {
                    return _Edit!!
                }
                _Edit = ImageVector.Builder(
                        name = "Edit",
                        defaultWidth = 24.dp,
                        defaultHeight = 24.dp,
                        viewportWidth = 960f,
                        viewportHeight = 960f
                ).apply {
                    path(fill = SolidColor(Color.Black)) {
                        moveTo(200f, 760f)
                        horizontalLineToRelative(57f)
                        lineToRelative(391f, -391f)
                        lineToRelative(-57f, -57f)
                        lineToRelative(-391f, 391f)
                        verticalLineToRelative(57f)
                        close()
                        moveTo(120f, 840f)
                        verticalLineToRelative(-170f)
                        lineToRelative(528f, -527f)
                        quadToRelative(12f, -11f, 26.5f, -17f)
                        reflectiveQuadToRelative(30.5f, -6f)
                        quadToRelative(16f, 0f, 31f, 6f)
                        reflectiveQuadToRelative(26f, 18f)
                        lineToRelative(55f, 56f)
                        quadToRelative(12f, 11f, 17.5f, 26f)
                        reflectiveQuadToRelative(5.5f, 30f)
                        quadToRelative(0f, 16f, -5.5f, 30.5f)
                        reflectiveQuadTo(817f, 313f)
                        lineTo(290f, 840f)
                        lineTo(120f, 840f)
                        close()
                        moveTo(760f, 256f)
                        lineTo(704f, 200f)
                        lineTo(760f, 256f)
                        close()
                        moveTo(619f, 341f)
                        lineTo(591f, 312f)
                        lineTo(648f, 369f)
                        lineTo(619f, 341f)
                        close()
                    }
                }.build()

                return _Edit!!
            }

        @Suppress("ObjectPropertyName")
        private var _Edit: ImageVector? = null

        val Add: ImageVector
            get() {
                if (_Add != null) {
                    return _Add!!
                }
                _Add = ImageVector.Builder(
                        name = "Add",
                        defaultWidth = 24.dp,
                        defaultHeight = 24.dp,
                        viewportWidth = 960f,
                        viewportHeight = 960f
                ).apply {
                    path(fill = SolidColor(Color.Black)) {
                        moveTo(440f, 520f)
                        horizontalLineTo(200f)
                        verticalLineToRelative(-80f)
                        horizontalLineToRelative(240f)
                        verticalLineToRelative(-240f)
                        horizontalLineToRelative(80f)
                        verticalLineToRelative(240f)
                        horizontalLineToRelative(240f)
                        verticalLineToRelative(80f)
                        horizontalLineTo(520f)
                        verticalLineToRelative(240f)
                        horizontalLineToRelative(-80f)
                        verticalLineToRelative(-240f)
                        close()
                    }
                }.build()

                return _Add!!
            }

        @Suppress("ObjectPropertyName")
        private var _Add: ImageVector? = null

        val Flashlight: ImageVector
            get() {
                if (_Flashlight != null) {
                    return _Flashlight!!
                }
                _Flashlight = ImageVector.Builder(
                        name = "Flashlight",
                        defaultWidth = 24.dp,
                        defaultHeight = 24.dp,
                        viewportWidth = 960f,
                        viewportHeight = 960f
                ).apply {
                    path(fill = SolidColor(Color.Black)) {
                        moveTo(280f, 880f)
                        verticalLineToRelative(-400f)
                        lineToRelative(-80f, -120f)
                        verticalLineToRelative(-280f)
                        horizontalLineToRelative(560f)
                        verticalLineToRelative(280f)
                        lineToRelative(-80f, 120f)
                        verticalLineToRelative(400f)
                        horizontalLineTo(280f)
                        close()
                        moveTo(480f, 620f)
                        quadToRelative(-25f, 0f, -42.5f, -17.5f)
                        reflectiveQuadTo(420f, 560f)
                        quadToRelative(0f, -25f, 17.5f, -42.5f)
                        reflectiveQuadTo(480f, 500f)
                        quadToRelative(25f, 0f, 42.5f, 17.5f)
                        reflectiveQuadTo(540f, 560f)
                        quadToRelative(0f, 25f, -17.5f, 42.5f)
                        reflectiveQuadTo(480f, 620f)
                        close()
                        moveTo(280f, 200f)
                        horizontalLineToRelative(400f)
                        verticalLineToRelative(-40f)
                        horizontalLineTo(280f)
                        verticalLineToRelative(40f)
                        close()
                        moveTo(680f, 280f)
                        horizontalLineTo(280f)
                        verticalLineToRelative(16f)
                        lineToRelative(80f, 120f)
                        verticalLineToRelative(384f)
                        horizontalLineToRelative(240f)
                        verticalLineToRelative(-384f)
                        lineToRelative(80f, -120f)
                        verticalLineToRelative(-16f)
                        close()
                    }
                }.build()

                return _Flashlight!!
            }

        @Suppress("ObjectPropertyName")
        private var _Flashlight: ImageVector? = null

    }
}
package jp.ryotn.panorama360.view.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

val Purple80 = Color(0xFFD0BCFF)
val PurpleGrey80 = Color(0xFFCCC2DC)
val Pink80 = Color(0xFFEFB8C8)

val Purple40 = Color(0xFF6650a4)
val PurpleGrey40 = Color(0xFF625b71)
val Pink40 = Color(0xFF7D5260)

fun ColorScheme.isLight() = this.background.luminance() > 0.5

val ColorScheme.topAppBarContainerColor: Color get() {
    return if (isLight()) Color.LightGray else Color.DarkGray
}

val ColorScheme.cameraViewBackgroundColor: Color get() {
    return if (isLight()) Color.LightGray else Color.DarkGray
}
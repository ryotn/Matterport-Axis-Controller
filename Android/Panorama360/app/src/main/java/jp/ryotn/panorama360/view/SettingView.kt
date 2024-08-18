package jp.ryotn.panorama360.view

import android.app.Application
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import jp.ryotn.panorama360.model.SettingViewModel
import jp.ryotn.panorama360.view.ui.theme.Panorama360Theme

@Composable
fun Setting(model: SettingViewModel) {
    val isGyro: Boolean by model.isGyro.collectAsState()

    Column {
        SwitchWithLabel(label = "雲台の回転停止検知に\nジャイロセンサーを利用する", state = isGyro) {
            model.putUseGyro(it)
        }
    }
}

@Composable
private fun SwitchWithLabel(label: String, state: Boolean, onStateChange: (Boolean) -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Switch,
                onClick = {
                    onStateChange(!state)
                }
            )
            .padding(8.dp)
            .fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween

    ) {

        Text(modifier = Modifier.padding(start = 24.dp)
            , text = label)
        Switch(modifier = Modifier.padding(end = 24.dp)
            , checked = state,
            onCheckedChange = {
                onStateChange(it)
            }
        )
    }
}

@Preview(showBackground = true)
@Composable
fun SettingPreview() {
    val model = SettingViewModel(Application())
    Panorama360Theme {
        Setting(model)
    }
}


@Preview(showBackground = true)
@Composable
fun SwitchButtonPreview() {
    Panorama360Theme {
        SwitchWithLabel("test", state = true, onStateChange = {})
    }
}
package com.veye.mobile.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.VisualTransformation
import com.veye.mobile.ui.theme.VeyeColors

@Composable
fun ChatInputField(
  value: String,
  onValueChange: (String) -> Unit,
  modifier: Modifier = Modifier,
  placeholder: String = "输入消息…",
  enabled: Boolean = true,
  maxLines: Int = 4,
  visualTransformation: VisualTransformation = VisualTransformation.None,
) {
  OutlinedTextField(
    value = value,
    onValueChange = onValueChange,
    modifier = modifier,
    enabled = enabled,
    placeholder = {
      Text(placeholder, color = VeyeColors.Muted)
    },
    maxLines = maxLines,
    textStyle = MaterialTheme.typography.bodyLarge.copy(color = VeyeColors.Ink),
    visualTransformation = visualTransformation,
    colors = OutlinedTextFieldDefaults.colors(
      focusedTextColor = VeyeColors.Ink,
      unfocusedTextColor = VeyeColors.Ink,
      disabledTextColor = VeyeColors.Muted,
      cursorColor = VeyeColors.Primary,
      focusedBorderColor = VeyeColors.PrimaryLight,
      unfocusedBorderColor = VeyeColors.Border,
      focusedContainerColor = VeyeColors.Surface,
      unfocusedContainerColor = VeyeColors.Surface,
      disabledContainerColor = VeyeColors.SurfaceMuted,
    ),
  )
}

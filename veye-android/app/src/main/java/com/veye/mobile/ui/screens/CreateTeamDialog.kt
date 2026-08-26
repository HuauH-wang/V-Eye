package com.veye.mobile.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier

@Composable
fun CreateTeamDialog(
  visible: Boolean,
  onDismiss: () -> Unit,
  onCreate: (name: String, description: String) -> Unit,
) {
  var name by remember { mutableStateOf("") }
  var description by remember { mutableStateOf("") }
  if (!visible) return

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("创建小队") },
    text = {
      Column {
        OutlinedTextField(
          value = name,
          onValueChange = { name = it },
          modifier = Modifier.fillMaxWidth(),
          singleLine = true,
          label = { Text("小队名称") },
        )
        OutlinedTextField(
          value = description,
          onValueChange = { description = it },
          modifier = Modifier.fillMaxWidth(),
          label = { Text("简介（可选）") },
        )
      }
    },
    confirmButton = {
      TextButton(
        onClick = {
          if (name.isNotBlank()) {
            onCreate(name.trim(), description.trim())
            onDismiss()
          }
        },
        enabled = name.isNotBlank(),
      ) { Text("创建") }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text("取消") }
    },
  )
}

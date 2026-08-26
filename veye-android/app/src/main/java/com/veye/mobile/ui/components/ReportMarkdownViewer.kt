package com.veye.mobile.ui.components

import android.text.method.LinkMovementMethod
import android.widget.TextView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.veye.mobile.cloud.CloudClient
import com.veye.mobile.ui.theme.VeyeColors
import io.noties.markwon.Markwon
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.image.coil.CoilImagesPlugin
import io.noties.markwon.linkify.LinkifyPlugin

private val VEYE_IMAGE = Regex("veye://image/([0-9a-fA-F-]+)")

/** 将报告 Markdown 中的 veye://image/ 占位符替换为可鉴权加载的 API URL */
fun preprocessReportMarkdown(markdown: String): String =
  markdown.replace(VEYE_IMAGE) { match ->
    CloudClient.recordImageUrl(match.groupValues[1])
  }

@Composable
fun ReportMarkdownViewer(
  markdown: String,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  val processed = remember(markdown) { preprocessReportMarkdown(markdown) }
  val markwon = remember(context) {
    Markwon.builder(context)
      .usePlugin(CoilImagesPlugin.create(context))
      .usePlugin(StrikethroughPlugin.create())
      .usePlugin(TablePlugin.create(context))
      .usePlugin(LinkifyPlugin.create())
      .build()
  }

  AndroidView(
    modifier = modifier,
    factory = { ctx ->
      TextView(ctx).apply {
        movementMethod = LinkMovementMethod.getInstance()
        setTextColor(VeyeColors.Ink.toArgb())
        textSize = 15f
        setLineSpacing(0f, 1.25f)
        setLinkTextColor(VeyeColors.PrimaryLight.toArgb())
      }
    },
    update = { textView ->
      markwon.setMarkdown(textView, processed)
    },
  )
}

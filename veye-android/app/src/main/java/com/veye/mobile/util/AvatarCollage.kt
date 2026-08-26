package com.veye.mobile.util

import com.veye.mobile.cloud.dto.MemberAvatarItemDto

data class CollageCell(
  val col: Int,
  val row: Int,
  val colSpan: Int = 1,
  val rowSpan: Int = 1,
)

data class CollageLayout(
  val cols: Int,
  val rows: Int,
  val cells: List<CollageCell>,
)

object AvatarCollage {
  private val colors = listOf(
    0xFF40916C.toInt(),
    0xFF577590.toInt(),
    0xFFF4A261.toInt(),
    0xFFE76F51.toInt(),
    0xFF90BE6D.toInt(),
    0xFF6A4C93.toInt(),
    0xFF43AA8B.toInt(),
  )

  fun initial(name: String): String {
    val t = name.trim()
    return if (t.isEmpty()) "?" else t.take(1).uppercase()
  }

  fun color(name: String): Int {
    var hash = 0
    for (ch in name) hash = (hash + ch.code) % colors.size
    return colors[hash.coerceAtLeast(0)]
  }

  fun layout(count: Int): CollageLayout {
    val n = count.coerceIn(1, 9)
    return when (n) {
      1 -> CollageLayout(1, 1, listOf(CollageCell(1, 1)))
      2 -> CollageLayout(2, 1, listOf(CollageCell(1, 1), CollageCell(2, 1)))
      3 -> CollageLayout(
        2, 2,
        listOf(CollageCell(1, 1), CollageCell(2, 1), CollageCell(1, 2, colSpan = 2)),
      )
      4 -> CollageLayout(
        2, 2,
        listOf(CollageCell(1, 1), CollageCell(2, 1), CollageCell(1, 2), CollageCell(2, 2)),
      )
      5 -> CollageLayout(
        6, 2,
        listOf(
          CollageCell(1, 1, colSpan = 3),
          CollageCell(4, 1, colSpan = 3),
          CollageCell(1, 2, colSpan = 2),
          CollageCell(3, 2, colSpan = 2),
          CollageCell(5, 2, colSpan = 2),
        ),
      )
      6 -> CollageLayout(
        3, 2,
        listOf(
          CollageCell(1, 1), CollageCell(2, 1), CollageCell(3, 1),
          CollageCell(1, 2), CollageCell(2, 2), CollageCell(3, 2),
        ),
      )
      7 -> CollageLayout(
        3, 3,
        listOf(
          CollageCell(1, 1), CollageCell(2, 1), CollageCell(3, 1),
          CollageCell(1, 2), CollageCell(2, 2), CollageCell(3, 2),
          CollageCell(2, 3),
        ),
      )
      8 -> CollageLayout(
        3, 3,
        listOf(
          CollageCell(1, 1), CollageCell(2, 1), CollageCell(3, 1),
          CollageCell(1, 2), CollageCell(2, 2), CollageCell(3, 2),
          CollageCell(1, 3), CollageCell(2, 3),
        ),
      )
      else -> CollageLayout(
        3, 3,
        (1..9).map { i ->
          CollageCell((i - 1) % 3 + 1, (i - 1) / 3 + 1)
        },
      )
    }
  }

  fun pickMembers(members: List<MemberAvatarItemDto>, limit: Int = 9): List<MemberAvatarItemDto> =
    members.take(limit)
}

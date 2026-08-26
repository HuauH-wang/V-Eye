package com.veye.mobile.ble

import java.io.File
import java.io.RandomAccessFile
import java.util.BitSet
import java.util.zip.CRC32

/**
 * 组包 + 缺片检测 + 整图 CRC32 校验（重传由外层发 RETX_REQ）。
 */
class ImageReceiver(
  private val meta: ImgMeta,
  private val outFile: File,
) {
  private val received = BitSet(meta.chunkCount)
  private val raf = RandomAccessFile(outFile, "rw")

  init {
    raf.setLength(meta.imgSize)
  }

  fun onChunk(chunk: ImgChunk) {
    if (chunk.sessionId != meta.sessionId) return
    if (chunk.chunkIndex < 0 || chunk.chunkIndex >= meta.chunkCount) return
    if (received.get(chunk.chunkIndex)) return

    val offset = meta.chunkSize.toLong() * chunk.chunkIndex.toLong()
    raf.seek(offset)
    raf.write(chunk.data)
    received.set(chunk.chunkIndex, true)
  }

  fun missingChunks(max: Int = 32): List<Int> {
    val out = ArrayList<Int>(max)
    for (i in 0 until meta.chunkCount) {
      if (!received.get(i)) {
        out.add(i)
        if (out.size >= max) break
      }
    }
    return out
  }

  fun isComplete(): Boolean = received.cardinality() == meta.chunkCount

  fun close() {
    raf.close()
  }

  fun verifyCrc32(): Boolean {
    raf.fd.sync()
    val crc = CRC32()
    RandomAccessFile(outFile, "r").use { r ->
      val buf = ByteArray(64 * 1024)
      var n: Int
      while (true) {
        n = r.read(buf)
        if (n <= 0) break
        crc.update(buf, 0, n)
      }
    }
    return (crc.value and 0xFFFFFFFFL) == (meta.crc32 and 0xFFFFFFFFL)
  }
}


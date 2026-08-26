package com.veye.mobile.cloud.dto

import com.squareup.moshi.FromJson
import com.squareup.moshi.JsonReader
import com.squareup.moshi.JsonWriter
import com.squareup.moshi.ToJson

/** 手写 adapter：VLM 的 details 字段可能是 string 或 string[]。 */
object IdentifyDetailsJsonAdapter {
  @FromJson
  fun fromJson(reader: JsonReader): IdentifyDetailsDto {
    var botanicalTraits = emptyList<String>()
    var toxicityEvidence = emptyList<String>()
    reader.beginObject()
    while (reader.hasNext()) {
      when (reader.nextName()) {
        "botanical_traits" -> botanicalTraits = readFlexibleStringList(reader)
        "toxicity_evidence" -> toxicityEvidence = readFlexibleStringList(reader)
        else -> reader.skipValue()
      }
    }
    reader.endObject()
    return IdentifyDetailsDto(
      botanical_traits = botanicalTraits,
      toxicity_evidence = toxicityEvidence,
    )
  }

  @ToJson
  fun toJson(writer: JsonWriter, value: IdentifyDetailsDto?) {
    if (value == null) {
      writer.nullValue()
      return
    }
    writer.beginObject()
    writer.name("botanical_traits")
    writeStringList(writer, value.botanical_traits)
    writer.name("toxicity_evidence")
    writeStringList(writer, value.toxicity_evidence)
    writer.endObject()
  }

  private fun readFlexibleStringList(reader: JsonReader): List<String> {
    return when (reader.peek()) {
      JsonReader.Token.STRING -> listOf(reader.nextString())
      JsonReader.Token.BEGIN_ARRAY -> {
        val out = mutableListOf<String>()
        reader.beginArray()
        while (reader.hasNext()) {
          when (reader.peek()) {
            JsonReader.Token.STRING -> out.add(reader.nextString())
            else -> reader.skipValue()
          }
        }
        reader.endArray()
        out
      }
      JsonReader.Token.NULL -> {
        reader.nextNull<Unit>()
        emptyList()
      }
      else -> {
        reader.skipValue()
        emptyList()
      }
    }
  }

  private fun writeStringList(writer: JsonWriter, value: List<String>) {
    writer.beginArray()
    value.forEach { writer.value(it) }
    writer.endArray()
  }
}

package com.mouya.phirachartstudio.engine

import java.util.Locale
import kotlin.math.roundToInt

object RpeSerializer {
    fun chartJson(chart: GeneratedChart, musicFile: String, backgroundFile: String): String {
        require(musicFile.matches(Regex("[A-Za-z0-9._ -]{1,120}"))) { "Invalid music filename" }
        require(backgroundFile.matches(Regex("[A-Za-z0-9._ -]{1,120}"))) { "Invalid background filename" }
        val notes = chart.notes.joinToString(",\n") { note ->
            val start = beatJson(note.beat)
            val end = beatJson(note.endBeat)
            """{"type":${note.kind.rpeType},"above":${if (note.above) 1 else 0},"startTime":$start,"endTime":$end,"positionX":${number(note.positionX)},"yOffset":0,"alpha":255,"hitsound":null,"size":${number(note.size)},"speed":1,"isFake":0,"visibleTime":${number(note.visibleTimeSeconds)},"tint":null,"tintHitEffects":null,"judgeArea":1.0}"""
        }
        val metaName = escape(chart.title)
        val level = "${chart.difficulty.displayName} ${chart.difficulty.rating.toInt()}"
        return """{
  "META": {
    "name": "${metaName}",
    "level": "${escape(level)}",
    "background": "${escape(backgroundFile)}",
    "charter": "Phira Chart Studio",
    "composer": "Unknown",
    "illustration": "Phira Chart Studio",
    "song": "${escape(musicFile)}",
    "offset": ${(chart.offsetSeconds * 1000f).roundToInt()},
    "RPEVersion": 160
  },
  "BPMList": [
    {"bpm": ${number(chart.bpm)}, "startTime": [0, 0, 1]}
  ],
  "judgeLineList": [
    {
      "Name": "Auto Generated Line",
      "Texture": "line.png",
      "father": -1,
      "rotateWithFather": false,
      "eventLayers": [{}],
      "notes": [
        $notes
      ],
      "isCover": 1,
      "zOrder": 0,
      "posControl": [],
      "sizeControl": [],
      "alphaControl": [],
      "yControl": []
    }
  ]
}
"""
    }

    fun infoYaml(chart: GeneratedChart, musicFile: String, backgroundFile: String, previewStart: Float = 0f): String {
        val level = "${chart.difficulty.displayName} ${chart.difficulty.rating.toInt()}"
        return """name: "${yaml(chart.title)}"
difficulty: ${number(chart.difficulty.rating)}
level: "${yaml(level)}"
charter: "Phira Chart Studio"
composer: "Unknown"
illustrator: "Phira Chart Studio"
chart: "chart.json"
format: rpe
music: "${yaml(musicFile)}"
illustration: "${yaml(backgroundFile)}"
previewStart: ${number(previewStart.coerceAtLeast(0f))}
previewEnd: ${number(minOf(chart.durationSeconds, previewStart + 30f))}
aspectRatio: 1.7777778
backgroundDim: 0.55
lineLength: 6.0
offset: 0.0
tags:
  - "Auto Generated"
  - "Offline Analysis"
intro: "Generated locally by Phira Chart Studio. Please review and edit before publishing."
holdPartialCover: false
noteUniformScale: false
forceAspectRatio: false
useRpe170Speed: false
useAttachUiFix: true
"""
    }

    private fun beatJson(position: BeatPosition): String =
        "[${position.whole},${position.numerator},${position.denominator}]"

    private fun number(value: Float): String = String.format(Locale.US, "%.5f", value)

    private fun escape(value: String): String = buildString(value.length) {
        value.forEach { char ->
            when (char) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> if (char.code < 0x20) append(' ') else append(char)
            }
        }
    }

    private fun yaml(value: String): String = value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ").replace("\r", " ")
}

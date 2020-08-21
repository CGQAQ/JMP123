package com.cgqaq

import jmp123.PlayBack
import jmp123.output.Audio
import java.io.IOException

object Main {
    @JvmStatic
    fun main(args: Array<String>) {
        val audio = Audio()
        val playBack = PlayBack(audio)
        try {
            playBack.open("/home/jason/code/cc/cc_jmp123/test.mp3", "I don't care")
            playBack.start(true)
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }
}
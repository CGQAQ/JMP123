/*
 * Audio.java -- 音频输出
 * Copyright (C) 2010
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 * If you would like to negotiate alternate licensing terms, you may do
 * so by contacting the author: <http://jmp123.sf.net/>
 */
package jmp123.output

import jmp123.decoder.Header
import jmp123.decoder.IAudio
import javax.sound.sampled.*
import kotlin.math.max
import kotlin.math.min

/**
 * 将解码得到的PCM数据写入音频设备（播放）。
 *
 */
class Audio() : IAudio {
    private var dateline: SourceDataLine? = null

    override lateinit var floatControl: FloatControl

    /**音量控制器  */
    var volControl: FloatControl? = null
    override fun open(h: Header, artist: String): Boolean {
        val af = AudioFormat(h.getSamplingRate().toFloat(), 16,
                h.channels, true, false)
        try {
            dateline = AudioSystem.getSourceDataLine(af) as SourceDataLine
            dateline!!.open(af, 8 * h.pcmSize)
            // dateline.open(af);
            volControl = dateline!!.getControl(FloatControl.Type.MASTER_GAIN) as FloatControl
        } catch (e: LineUnavailableException) {
            System.err.println("初始化音频输出失败。")
            return false
        } catch (e: IllegalArgumentException) {
            System.err.println("加载音量控制失败。")
        }
        dateline!!.start()
        return true
    }

    override fun write(b: ByteArray, size: Int): Int {
        return dateline!!.write(b, 0, size)
    }

    override fun start(b: Boolean) {
        if (dateline == null) return
        if (b) dateline!!.start() else dateline!!.stop()
    }

    //=============添加的方法===========
    override fun setLineGain(gain: Float) {
        if (volControl != null) {
            val newGain = min(max(gain, volControl!!.minimum), volControl!!.maximum)
            volControl!!.value = newGain
        }
    }

    //==================================
    override fun drain() {
        if (dateline != null) dateline!!.drain()
    }

    override fun close() {
        if (dateline != null) {
            dateline!!.stop()
            dateline!!.close()
        }
    }

    override fun refreshMessage(msg: String) {
        print(msg)
    }
}
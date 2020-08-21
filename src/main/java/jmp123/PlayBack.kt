/*
 * PlayBack.java -- 播放一个文件
 * Copyright (C) 2011
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
 * so by contacting the author: <http://jmp123.sourceforge.net/>
 */
package jmp123

import jmp123.decoder.*
import jmp123.instream.BuffRandReadFile
import jmp123.instream.BuffRandReadURL
import jmp123.instream.MultiplexAudio
import jmp123.instream.RandomRead
import org.websoft.widget.SpectrumPane
import java.io.IOException
import java.util.concurrent.locks.ReentrantLock
import javax.swing.JSlider

/**
 * 播放一个文件及播放时暂停等控制。用PlayBack播放一个文件的步骤为：
 *
 *  1. 用构造器 [.PlayBack] 创建一个PlayBack对象；
 *  1. 调用PlayBack对象的 [.open] 打开源文件；
 *
 *  * 可以调用PlayBack对象的 [.getHeader] 方法获取 [jmp123.decoder.Header] 对象；
 *  * 可以调用PlayBack对象的 [.getID3Tag] 方法获取 [jmp123.decoder.ID3Tag] 对象；
 *
 *  1. 调用PlayBack对象的 [.start] 开始播放；
 *
 *  * 可调用PlayBack对象的 [.pause] 方法控制播放暂停或继续；
 *  * 可调用PlayBack对象的 [.stop] 方法终止播放；
 *
 *  1. 播放完一个文件，调用PlayBack对象的 [.close] 作必要的清理。
 *
 *
 */
class PlayBack(private val audio: IAudio?) {
    private val buf: ByteArray
    private val BUFLEN = 8192
    private var eof = false
    private var paused = false
    private var instream: RandomRead? = null

    private val lock = ReentrantLock()
    private val condition = lock.newCondition()

    /**
     * 获取文件的标签信息。
     *
     * @return 文件的标签信息 [jmp123.decoder.ID3Tag] 对象。
     * @see jmp123.decoder.ID3Tag
     */
    val iD3Tag: ID3Tag?
    private var off = 0
    private var maxOff = 0

    /**
     * 获取帧头信息。
     *
     * @return 取帧 [jmp123.decoder.Header] 对象。
     * @see jmp123.decoder.Header
     */
    val header: Header
    var vloume = 0.0f

    /**
     * 暂停或继续此文件播放。这相当于一个单稳态的触发开关，第一次调用该方法暂停播放，第二次调用继续播放，以此类推。
     * @return 返回当前状态。处于暂停状态返回true，否则返回false。
     */
    fun pause(): Boolean {
        audio!!.start(paused)
        if (paused) {
            synchronized(this) { condition.signal() }
        }
        paused = !paused
        return paused
    }

    /**
     * 终止此文件播放。
     */
    fun stop() {
        eof = true
        synchronized(this) { condition.signal() }
        if (instream != null) instream!!.close()
    }

    /**
     * 关闭此文件播放并清除关联的资源。
     */
    fun close() {
        iD3Tag?.clear()
        audio?.close()

        // 若正读取网络文件通过调用close方法中断下载(缓冲)
        if (instream != null) instream!!.close()
        //System.out.println("jmp123.PlayBack.close() ret.");
    }

    /**
     * 打开文件并解析文件信息。
     *
     * @param name
     * 文件路径。
     * @param title
     * 歌曲标题，可以为null。
     * @return 打开失败返回 **false**；否则返回 **true** 。
     * @throws IOException 发生I/O错误。
     */
    @Throws(IOException::class)
    fun open(name: String, title: String?): Boolean {
        off = 0
        maxOff = off
        eof = false
        paused = eof
        var id3v1 = false
        val str = name.toLowerCase()
        if (str.startsWith("http://") && str.endsWith(".mp3")) {
            instream = BuffRandReadURL(audio)
        } else if (str.endsWith(".mp3")) {
            instream = BuffRandReadFile()
            id3v1 = true
        } else if (str.endsWith(".dat") || str.endsWith(".vob")) {
            instream = MultiplexAudio()
        } else {
            System.err.println("Invalid File Format.")
            return false
        }
        if (!instream!!.open(name, title)) return false
        val tagSize = parseTag(id3v1)
        if (tagSize == -1) return false

        // 初始化header. 设置文件的音乐数据长度,用于CBR格式的文件计算帧数
        header.initialize(instream!!.length() - tagSize, instream!!.duration)

        // 定位到第一帧并完成帧头解码
        nextHeader()
        if (eof) return false
        if (audio != null && title != null) {
            // 歌曲的标题和艺术家，优先使用播放列表(*.m3u)中指定的参数
            val strArray = title.split(" ".toRegex()).toTypedArray()
            if (strArray.size >= 2) {
                // if (id3Tag.getTitle() == null)
                iD3Tag!!.settTitle(strArray[0])
                // if (id3Tag.getArtist() == null)
                iD3Tag.settArtist(strArray[1])

                /*StringBuilder strbuilder = new StringBuilder();
				strbuilder.append(id3Tag.getTitle());
				strbuilder.append('@');
				strbuilder.append(id3Tag.getArtist());
				audio.refreshMessage(strbuilder.toString());*/
            }
        }

        // 成功解析帧头后初始化音频输出
        if (audio != null && !audio.open(header, iD3Tag!!.artist)) return false

        ////////////////////////
        ////添加方法/////////////
        ////////////////////////
        if (audio is SpectrumPane) {
            audio.setPlayFileName(title!!.substring(0, title.lastIndexOf(".")))
        }
        return true
    }

    @Throws(IOException::class)
    private fun parseTag(id3v1: Boolean): Int {
        var tagSize = 0

        // ID3 v1
        if (id3v1 && instream!!.seek(instream!!.length() - 128 - 32)) {
            if (instream!!.read(buf, 0, 128 + 32) == 128 + 32) {
                if (iD3Tag!!.checkID3V1(buf, 32)) {
                    tagSize = 128
                    iD3Tag.parseID3V1(buf, 32)
                }
            } else return -1
            instream!!.seek(0)
            tagSize += iD3Tag!!.checkAPEtagFooter(buf, 0) // APE tag footer
        }
        if (instream!!.read(buf, 0, BUFLEN).also { maxOff = it } <= 10) {
            eof = true
            return -1
        }

        // ID3 v2
        var sizev2 = iD3Tag!!.checkID3V2(buf, 0)
        tagSize += sizev2
        if (sizev2 > maxOff) {
            val b = ByteArray(sizev2)
            System.arraycopy(buf, 0, b, 0, maxOff)
            sizev2 -= maxOff
            if (instream!!.read(b, maxOff, sizev2).also { maxOff = it } < sizev2) {
                eof = true
                return -1
            }
            iD3Tag.parseID3V2(b, 0, b.size)
            if (instream!!.read(buf, 0, BUFLEN).also { maxOff = it } <= 4) eof = true
        } else if (sizev2 > 10) {
            iD3Tag.parseID3V2(buf, 0, sizev2)
            off = sizev2
        }
        return tagSize
    }

    /**
     * 解码已打开的文件。
     *
     * @param verbose
     * 指定为 **true** 在控制台打印播放进度条。
     * @return 成功播放指定的文件返回true，否则返回false。
     */
    fun start(verbose: Boolean): Boolean {
        var layer: Layer123? = null
        var frames = 0
        paused = false
        layer = when (header.layer) {
            1 -> Layer1(header, audio)
            2 -> Layer2(header, audio)
            3 -> Layer3(header, audio)
            else -> return false
        }
        try {
            while (!eof) {
                // 1. 解码一帧并输出(播放)
                off = layer.decodeFrame(buf, off)
                if (verbose && ++frames and 0x7 == 0) header.printProgress()

                // 2. 定位到下一帧并解码帧头
                nextHeader()

                // 3. 检测并处理暂停
                if (paused) {
                    synchronized(this) { while (paused && !eof) condition.await() }
                }
            }
            if (verbose) {
                header.printProgress()
                println("\n")
            }
        } catch (e: IOException) {
            e.printStackTrace()
            return false
        } catch (e: InterruptedException) {
            // System.out.println("jmp123.PlayBack.start() interrupt.");
        } finally {
            layer?.close()
        }
        // System.out.println("jmp123.PlayBack.start() ret.");
        return true
    }
    //=======================添加的方法=====================
    /**
     * 播放从start帧到end的音乐段
     * @param start 开始帧的位置
     * @param end 结束帧的位置
     * @throws IOException
     */
    fun start(start: Long, end: Long, progressBar: JSlider?, volumeBar: JSlider?): Boolean {
        //当前帧的总长度
        var start = start
        var end = end
        val frameCount = header.trackFrames
        if (end > frameCount) end = frameCount
        var layer: Layer123? = null //, layer2 = null;
        paused = false
        layer = when (header.layer) {
            1 -> Layer1(header, audio)
            2 -> Layer2(header, audio)
            3 -> Layer3(header, audio)
            else -> return false
        }
        try {
            while (!eof && end-- > 0) {
                if (start-- > 0) {
                    // 1.1. 解码一帧不输出(不播放)
                    // 如果不播放则可以不需要去解码这个帧，则直接跳过这一帧
//					off = layer2.decodeFrame(buf, off); 
                } else {
                    //设置音量
                    //
                    if (volumeBar != null) {
                        val currentValue = volumeBar.value.toFloat()
                        val maxValue = 0f //audio.getFloatControl().getMaximum();
                        val minValue = audio!!.floatControl.minimum + 30
                        audio.setLineGain(currentValue / 100 * (maxValue - minValue) + minValue)
                    } else {
                        audio!!.setLineGain(vloume)
                    }
                    // 1.2. 解码一帧并输出(播放)
                    off = layer.decodeFrame(buf, off)
                }
                // 2. 定位到下一帧并解码帧头
                nextHeader()

                // 3. 检测并处理暂停
                if (paused) {
                    synchronized(this) { while (paused && !eof) condition.await() }
                }
            }
        } catch (e: IOException) {
            e.printStackTrace()
            return false
        } catch (e: InterruptedException) {
            e.printStackTrace()
        } finally {
            layer?.close()
        }
        return true
    }

    //====================================================================
    @Throws(IOException::class)
    private fun nextHeader() {
        var len: Int
        var chunk = 0
        while (!eof && !header.syncFrame(buf, off, maxOff)) {
            // buf内帧同步失败或数据不足一帧，刷新缓冲区buf
            off = header.offset()
            len = maxOff - off
            System.arraycopy(buf, off, buf, 0, len)
            maxOff = len + instream!!.read(buf, len, off)
            off = 0
            if (maxOff <= len || BUFLEN.let { chunk += it; chunk } > 0x10000) eof = true
        }
        off = header.offset()
    }

    /**
     * 用指定的音频输出对象构造一个PlayBack对象。
     *
     * @param audio
     * 指定的音频输出 [jmp123.decoder.IAudio] 对象。若指定为 **null** 则只解码不播放输出。
     * @see jmp123.output.Audio
     */
    init {
        header = Header()
        iD3Tag = ID3Tag()
        buf = ByteArray(BUFLEN)
    }
}
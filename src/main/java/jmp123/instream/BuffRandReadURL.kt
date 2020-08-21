/*
* BuffRandAcceURL.java -- (HTTP协议)读取远程文件.
* Copyright (C) 2010
* 没有用java.util.concurrent实现读写同步,可能不是个好主意.
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
*
*/
package jmp123.instream

import jmp123.decoder.IAudio
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.URL
import java.net.URLDecoder
import java.util.concurrent.locks.ReentrantLock

/**
 * 读取网络文件，带缓冲区。
 */
class BuffRandReadURL(private val audio: IAudio?) : RandomRead() {
    private var offset // 相对于文件首的偏移量
            = 0
    private val lock // 读和写(缓冲)互斥
            : ByteArray
    private val buf // 同时作写线程同步锁
            : ByteArray
    private var bufsize // buf已填充的字节数
            = 0
    private var acceptRanges // true: 目标文件可随机读取定位
            = false

    @Volatile
    private var eof // true: 文件已经下载完.
            = false
    private val connection: HttpConnection


    private val lockShit = ReentrantLock()
    private val condition = lockShit.newCondition()

    @Throws(IOException::class)
    override fun open(spec: String?, title: String?): Boolean {
        var s1 = spec!!.substring(spec.lastIndexOf("/") + 1)
        val s2 = URLDecoder.decode(s1, "GBK")
        s1 = URLDecoder.decode(s1, "UTF-8")
        if (s1.length > s2.length) s1 = s2
        if (audio != null && title != null) audio.refreshMessage(title)
        connection.open(URL(spec), null)
        val code = connection.responseCode
        if (code < 200 || code >= 300) return printErrMsg("URL Connection Fails. ResponseCode: " + code
                + ", ResponseMessage: " + connection.responseMessage)

        /*s2 = connect.getHeaderField("Content-Type");
		if(s2 == null || s2.startsWith("audio") == false)
			return printErrMsg("Illegal Content-Type: " + s2);*/length = connection.contentLength
        if (length <= 0) return printErrMsg("Failed to get file length.")
        println("\nPLAY>> $s1")
        acceptRanges = "bytes" == connection.getHeaderField("Accept-Ranges")
        //if(!acceptRanges)
        //	System.out.println(url.getHost() + ": not support multi-threaded downloads.");

        // 创建"写"线程
        val w: Writer = Writer()
        w.name = "writer_thrad"
        w.priority = Thread.NORM_PRIORITY + 2
        w.start()
        return true
    }

    private fun printErrMsg(msg: String): Boolean {
        println()
        connection.printResponse()
        System.err.println(msg)
        return false
    }

    override fun read(b: ByteArray?, off: Int, len: Int): Int {
        // 1.等待缓冲区有足够内容可读
        var len = len
        synchronized(lock) {
            while (bufsize < len && !eof) {
                try {
                    waitForBuffering()
                } catch (e: InterruptedException) {
                    return -1
                }
            }
            if (bufsize == 0) return -1
            if (bufsize < len) len = bufsize
        }

        // 2.从缓冲区读取
        val srcOff = offset and OFFMASK
        val bytes = BUFLEN - srcOff
        if (bytes < len) {
            System.arraycopy(buf, srcOff, b, off, bytes)
            System.arraycopy(buf, 0, b, off + bytes, len - bytes)
        } else System.arraycopy(buf, srcOff, b, off, len)
        synchronized(lock) { bufsize -= len }
        offset += len

        // 3.通知"写"线程
        synchronized(buf) { condition.signal() }
        return len
    }

    @Throws(InterruptedException::class)
    private fun waitForBuffering() {
        var msg: String
        var kbps: Float
        var t: Long
        val t1 = System.currentTimeMillis()
        while (bufsize < BUFFERSIZE && !eof) {
            condition.await()
            if (System.currentTimeMillis() - t1.also { t = it } < 200) continue
            kbps = (BUFLEN shr 10).toFloat() * 1000 / t
            msg = String.format("\rbuffered: %6.2f%%, %6.02fKB/s ",
                    100f * bufsize / BUFLEN, kbps)
            if (audio != null) audio.refreshMessage(msg) else print(msg)
            if (t > 10000 && kbps < 8) {
                println("\nDownloading speed too slow,please try again later.")
                close()
                break
            }
        }
        print("\n")
    }

    val filePointer: Long
        get() = offset.toLong()

    override fun close() {
        // 结束Writer线程
        eof = true
        synchronized(buf) { condition.signal() }
        try {
            connection.close()
        } catch (e: IOException) {
        }
    }

    override fun seek(pos: Long): Boolean {
        return if (acceptRanges == false) false else false
        // 尚未完善随机读取定位...
    }

    //=========================================================================
    private inner class Writer : Thread() {
        override fun run() {
            var len: Int
            var off = 0
            var rema = 0
            var retry = 0
            var bytes: Long = 0
            val instream = connection.inputStream ?: return
            try {
                while (!eof) {
                    // 1.等待空闲块
                    if (retry == 0) {
                        while (!eof) {
                            if (bufsize <= BUFFERSIZE) break
                            synchronized(buf) { condition.await() }
                        }
                        off = off and OFFMASK
                        rema = BLOCKLEN
                    }

                    // 2.下载一块,超时重试10次
                    try {
                        while (rema > 0 && !eof) {
                            len = if (rema < 4096) rema else 4096 // 每次读几K合适?
                            if (instream.read(buf, off, len).also { len = it } == -1) {
                                eof = true
                                break
                            }
                            rema -= len
                            off += len
                            if (len.let { bytes += it; bytes } >= length) {
                                eof = true
                                break
                            }
                            //System.out.printf("bytes=%,d  len=%d  rema=%d\n", bytes, len, rema);
                        }
                    } catch (e: SocketTimeoutException) {
                        retry++
                        System.out.printf("[B# %,d] Timeout, retry=%d\n", bytes, retry)
                        if (retry < 10) continue
                        System.out.printf("B# %,d: out of retries. Giving up.\n", bytes)
                        eof = true // 终止下载
                    }
                    retry = 0

                    // 3.通知读线程
                    synchronized(lock) {
                        bufsize += BLOCKLEN - rema
                        condition.signal()
                    }
                }
            } catch (e: Exception) {
                println("BuffRandReadURL.Writer.run(): $e")
            } finally {
                eof = true
                synchronized(lock) { condition.signal() }
                try {
                    instream.close()
                } catch (e: IOException) {
                }
            }
            //System.out.println("\nBuffRandReadURL.Writer.run() ret.");
        }
    }

    companion object {
        private const val BLOCKLEN = 4096 * 8 //32K
        private const val BUFLEN = BLOCKLEN shl 4
        private const val OFFMASK = BUFLEN - 1
        private const val BUFFERSIZE = BUFLEN - BLOCKLEN
    }

    /**
     * 创建一个读取网络文件的对象。并不会由audio指定的音频输出对象产生任何音频输出，仅仅使用audio定时刷新并显示缓冲等信息。
     * @param audio 音频输出对象。如果为**null**，不显示读取过程中缓冲等信息。
     */
    init {
        buf = ByteArray(BUFLEN)
        lock = ByteArray(0)
        connection = HttpConnection()
    }
}
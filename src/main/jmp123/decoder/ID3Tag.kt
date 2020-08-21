/*
* ID3Tag.java -- 解析MP3文件的ID3 v1/v2 tag.
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
* so by contacting the author: <http://jmp123.sourceforge.net/>
*/
package jmp123.decoder

import java.nio.charset.Charset

/**
 * 解析MP3文件的ID3 v1/v2 tag的部分信息。<br></br>
 * ID3 v1: 128-byte，通常位于文件尾。<br></br>
 * [0-2] 3-byte: ID3 v1标识 ，为'TAG'表示接下来的125字节为ID3 v1的标题等域。<br></br>
 * [3—32] 30-byte: 标题<br></br>
 * [33—62] 30-byte: 艺术家<br></br>
 * [63—92] 30-byte: 专辑名<br></br>
 * [93—96] 4-byte: 发行年份<br></br>
 * [97—126] 30-byte: v1.0 -- 注释/附加/备注信息； v1.1 -- 前29字节为注释/附加/备注信息，最后1字节为音轨信息<br></br>
 * [127] 1-byte : 流派
 *
 *
 * ID3 v2.2/2.3/2.4不同版本的帧结构不同，所以高版本不兼容低版本。详情见官网：<br></br>
 * [id3 v2.2](http://www.id3.org/id3v2-00)<br></br>
 * [id3 v2.3](http://www.id3.org/id3v2.3.0)<br></br>
 * [id3 v2.4](http://www.id3.org/id3v2.4.0-structure)<br></br>
 *
 *
 * ID2 v2 支持MP3文件内置歌词文本和唱片集图片，实际的情况是，很多网络MP3文件利用它在MP3文件内置广告。所以ID3Tag没有解析内置的歌词，
 * 如果你对其内置的图片感兴趣，可以用[.getPicture]方法获取，图片的媒体类型为"image/png" 或 "image/jpeg"。
 *
 *
 * 网络MP3文件中ID3 v1/v2的标题、艺术家、专辑等域被篡改为某网站的网址或者某人的QQ号，这一情况并不少见，所以ID3Tag在解析时对这些文本
 * 域进行了过滤处理， 这导致你用其它播放器时得到的这些域的信息与ID3Tag解析结果不一致。
 */
class ID3Tag {
    /**
     * 获取文件中内置的唱片集图片。
     * @return 唱片集图片。图片的MIME类型为"image/png" 或 "image/jpeg"，返回null表示文件没有内置的唱片集图片。
     */
    var picture: ByteArray? = null
        private set

    /**
     * 获取歌曲标题。
     * @return 歌曲标题。
     */
    // ID3v1 & ID3v2
    var title: String? = null
        private set

    /**
     * 获取歌曲艺术家。
     * @return 歌曲艺术家。
     */
    var artist: String? = null
        private set

    /**
     * 获取歌曲唱片集。
     * @return 歌曲唱片集。
     */
    var album: String? = null
        private set

    /**
     * 获取歌曲发行年份。
     * @return 歌曲发行年份。
     */
    var year: String? = null
        private set

    // ID3v2
    //private String lyrics; // (内嵌的)歌词
    private var version = 0
    private var exheaderSize = 0
    private var haveID3v2Footer = false

    //TEXT_ENCODING[0]应由ISO-8859-1改为GBK ?
    private val TEXT_ENCODING = arrayOf("GBK", "UTF-16", "UTF-16BE", "UTF-8")
    //--------------------------------------------------------------------
    // ID3v1 & ID3v2
    /**
     * 在控制台打印标签信息。
     */
    fun printTag() {
        //if (lyrics != null)
        //	System.out.println("\r" + lyrics + "\n");
        if (title != null) println("      [ Title] $title")
        if (artist != null) println("      [Artist] $artist")
        if (album != null) println("      [ Album] $album")
        if (year != null) println("      [  Year] $year")
    }

    /**
     * 清除标签信息。
     */
    fun clear() {
        year = null
        album = year
        artist = album
        title = artist
        //lyrics = null;
        exheaderSize = 0
        version = exheaderSize
        haveID3v2Footer = false
        picture = null
    }

    /**
     * 设置歌曲标题指定为指定值。
     * @param title 歌曲标题。
     */
    fun settTitle(title: String?) {
        this.title = title
    }

    /**
     * 设置歌曲艺术家指定为指定值。
     * @param artist 艺术家。
     */
    fun settArtist(artist: String?) {
        this.artist = artist
    }
    // ID3v1 ------------------------------------------------------------------
    /**
     * 检测是否有ID3 v1标签信息。源数据可用长度不少于3字节。
     *
     * @param b
     * 源数据。
     * @param off
     * 源数据偏移量。
     * @return 有ID3 v1标签信息返回true，否则返回false。
     */
    fun checkID3V1(b: ByteArray, off: Int): Boolean {
        return b[off].toChar() == 'T' && b[off + 1].toChar() == 'A' && b[off + 2].toChar() == 'G'
    }

    /**
     * 解析ID3 v1标签信息。源数据可用长度不少于128字节。
     *
     * @param b
     * 源数据。
     * @param off
     * 源数据偏移量。
     */
    fun parseID3V1(b: ByteArray, off: Int) {
        var i: Int
        if (b.size < 128 || !checkID3V1(b, off)) return
        var buf: ByteArray? = ByteArray(125)
        System.arraycopy(b, 3 + off, buf, 0, 125)
        i = 0
        while (i < 30 && buf!![i].toInt() != 0) {
            i++
        }
        try {
            //由ISO-8859-1改为GBK ?
            if (title == null) title = String(buf!!, 0, i, Charset.forName("gbk")) //.replaceAll("[^\u4e00-\u9fa5]", "");
            if (title!!.isEmpty()) title = null
            i = 30
            while (i < 60 && buf!![i].toInt() != 0) {
                i++
            }
            if (artist == null) artist = String(buf!!, 30, i - 30, Charset.forName("gbk")).replace("[^\u4e00-\u9fa5]".toRegex(), "")
            if (artist!!.length == 0) artist = null
            i = 60
            while (i < 90 && buf!![i].toInt() != 0) {
                i++
            }
            if (album == null) album = String(buf!!, 60, i - 60, Charset.forName("gbk")) //.replaceAll("[^\u4e00-\u9fa5]", "");
            if (album!!.length == 0) album = null
            i = 90
            while (i < 94 && buf!![i].toInt() != 0) {
                i++
            }
            if (year == null) year = String(buf!!, 90, i - 90, Charset.forName("gbk")).replace("[^0-9]".toRegex(), "")
            if (year!!.length == 0) year = null
        } catch (e: Exception) {
            e.printStackTrace()
        }
        buf = null
    }
    // ID3v2 ------------------------------------------------------------------
    /**
     * 获取ID3 v2标签信息长度。源数据可用长度不少于头信息长度10字节。
     *
     * @param b
     * 源数据。
     * @param off
     * 源数据偏移量。
     * @return 标签信息长度，单位“字节”。以下两种情况返回0：
     *
     *  * 如果源数据b偏移量off开始的数据内未检测到ID3 v2标签信息；
     *  * 如果源数据b的可用长度少于少于头信息长度10字节。
     *
     */
    fun checkID3V2(b: ByteArray, off: Int): Int {
        if (b.size - off < 10) return 0
        if (b[off].toChar() != 'I' || b[off + 1].toChar() != 'D' || b[off + 2].toChar() != '3') return 0
        version = b[off + 3].toInt() and 0xff
        if (version > 2 && b[off + 5].toInt() and 0x40 != 0) exheaderSize = 1 // 设置为1表示有扩展头
        haveID3v2Footer = b[off + 5].toInt() and 0x10 != 0
        var size = synchSafeInt(b, off + 6)
        size += 10 // ID3 header:10-byte
        return size
    }

    /**
     * 解析ID3 v2标签信息。从源数据b偏移量off开始的数据含ID3 v2头信息的10字节。
     *
     * @param b
     * 源数据。
     * @param off
     * 源数据偏移量。
     * @param len
     * 源数据长度。
     */
    fun parseID3V2(b: ByteArray, off: Int, len: Int) {
        var max_size = off + len
        var pos = off + 10 //ID3 v2 header:10-byte
        if (exheaderSize == 1) {
            exheaderSize = synchSafeInt(b, off)
            pos += exheaderSize
        }
        max_size -= 10 //1 frame header: 10-byte
        if (haveID3v2Footer) max_size -= 10

        //System.out.println("ID3 v2." + version);
        while (pos < max_size) pos += getFrame(b, pos, max_size)
    }

    private fun synchSafeInt(b: ByteArray, off: Int): Int {
        var i: Int = b[off].toInt() and 0x7f shl 21
        i = i or (b[off + 1].toInt() and 0x7f shl 14)
        i = i or (b[off + 2].toInt() and 0x7f shl 7)
        i = i or (b[off + 3].toInt() and 0x7f)
        return i
    }

    private fun byte2int(b: ByteArray, off: Int, len: Int): Int {
        var i: Int
        var ret: Int = b[off].toInt() and 0xff
        i = 1
        while (i < len) {
            ret = ret shl 8
            ret = ret or (b[off + i].toInt() and 0xff)
            i++
        }
        return ret
    }

    private fun getFrame(b: ByteArray, off: Int, endPos: Int): Int {
        var off = off
        var id_part = 4
        var frame_header = 10
        if (version == 2) {
            id_part = 3
            frame_header = 6
        }
        val id = String(b, off, id_part)
        off += id_part // Frame ID
        val fsize: Int
        var len: Int
        if (version <= 3) {
            len = byte2int(b, off, id_part)
            fsize = len //Size  $xx xx xx xx
        } else {
            len = synchSafeInt(b, off)
            fsize = len //Size 4 * %0xxxxxxx
        }
        if (fsize < 0) // 防垃圾数据
            return frame_header
        off += id_part // frame size = frame id bytes
        if (version > 2) off += 2 // flag: 2-byte
        val enc = b[off].toInt()
        len-- // Text encoding: 1-byte
        off++ // Text encoding: 1-byte
        if (len <= 0 || off + len > endPos || enc < 0 || enc >= TEXT_ENCODING.size) return fsize + frame_header
        //System.out.println(len+" -------- off = " + off);
        //System.out.println("ID: " + id + ", id.hashCode()=" + id.hashCode());
        //System.out.println("text encoding: " + TEXT_ENCODING[enc]);
        //System.out.println("frame size: " + fsize);
        //if(off>=171)
        //	System.out.println(len+" -------- off = " + off);
        try {
            when (id.hashCode()) {
                83378, 2575251 -> if (title == null) title = String(b, off, len, Charset.forName(TEXT_ENCODING[enc])).replace("[^\u4e00-\u9fa5]".toRegex(), "")
                83552, 2590194 -> if (year == null) year = String(b, off, len, Charset.forName(TEXT_ENCODING[enc])).replace("[^0-9]".toRegex(), "")
                2569358 -> {
                }
                82815, 2567331 -> if (album == null) album = String(b, off, len, Charset.forName(TEXT_ENCODING[enc])).replace("[^\u4e00-\u9fa5]".toRegex(), "")
                83253, 2581512 -> if (artist == null) {
                    artist = String(b, off, len, Charset.forName(TEXT_ENCODING[enc]))
                    artist = artist!!.split("[^\u4e00-\u9fa5]".toRegex()).toTypedArray()[0]
                }
                2583398 -> {
                }
                2015625 -> {
                    //MIMEtype: "image/png" or "image/jpeg"
                    id_part = off
                    while (b[id_part].toInt() != 0 && id_part < endPos) {
                        id_part++
                    }
                    val MIMEtype = String(b, off, id_part - off, Charset.forName(TEXT_ENCODING[enc]))
                    println("[APIC MIME type] $MIMEtype")
                    len -= id_part - off + 1
                    off = id_part + 1
                    val picture_type: Int = b[off].toInt() and 0xff
                    println("[APIC Picture type] $picture_type")
                    off++ //Picture type
                    len--
                    id_part = off
                    while (b[id_part].toInt() != 0 && id_part < endPos) {
                        id_part++
                    }
                    println("[APIC Description] "
                            + String(b, off, id_part - off, Charset.forName(TEXT_ENCODING[enc])))
                    len -= id_part - off + 1
                    off = id_part + 1
                    //<text string according to encoding> $00 (00)
                    if (b[off].toInt() == 0) { //(00)
                        len--
                        off++
                    }
                    //Picture data (binary data): 从b[off]开始的len字节
                    if (picture == null) {
                        picture = ByteArray(len)
                        System.arraycopy(b, off, picture, 0, len)
                        // 内置于MP3的图片存盘
                        /* try {
						String ext = MIMEtype.substring(MIMEtype.indexOf('/')+1);
						FileOutputStream fos = new FileOutputStream("apic."+ext);
						fos.write(b, off, len);
						fos.flush();
						fos.close();
					} catch (Exception e) {}*/
                    }
                }
            }
        } catch (e: Exception) {
        }
        return fsize + frame_header
    }

    /**
     * 获取APE标签信息版本。
     * @return 以整数形式返回APE标签信息版本。
     */
    // APE tag ----------------------------------------------------------------
    var apeVer = 0
        private set

    private fun apeInt32(b: ByteArray, off: Int): Int {
        return if (b.size - off < 4) 0 else b[off + 3].toInt() and 0xff shl 24 or (b[off + 2].toInt() and 0xff shl 16) or (b[off + 1].toInt() and 0xff shl 8) or (b[off].toInt() and 0xff)
    }

    /**
     * 获取APE标签信息长度。源数据b的可用长度不少于32字节。
     *
     * @param b
     * 源数据。
     * @param off
     * 源数据偏移量。
     * @return APE标签信息长度。以下两种情况返回0：
     *
     *  * 如果源数据b偏移量off开始的数据内未检测到APE标签信息；
     *  * 如果源数据b的可用长度少于32字节。
     *
     */
    fun checkAPEtagFooter(b: ByteArray, off: Int): Int {
        if (b.size - off < 32) return 0
        if (b[off].toChar() == 'A' && b[off + 1].toChar() == 'P' && b[off + 2].toChar() == 'E' && b[off + 3].toChar() == 'T' && b[off + 4].toChar() == 'A' && b[off + 5].toChar() == 'G' && b[off + 6].toChar() == 'E' && b[off + 7].toChar() == 'X') {
            apeVer = apeInt32(b, off + 8)
            return apeInt32(b, off + 12) + 32
        }
        return 0
    }
}
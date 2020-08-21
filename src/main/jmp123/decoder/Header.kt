/*
* Header.java --MPEG-1/2/2.5 Audio Layer I/II/III 帧同步和帧头信息解码
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
* so by contacting the author: <http://jmp123.sourceforge.net/>.
*/
package jmp123.decoder

/**
 * 帧同步及帧头信息解码。
 */
class Header {
    //public static final int MAX_FRAMESIZE = 1732;	//MPEG 1.0/2.0/2.5, Layer 1/2/3
    /*
	 * bitrate[lsf][layer-1][bitrate_index]
	 */
    private val bitrate = arrayOf(arrayOf(intArrayOf(0, 32, 64, 96, 128, 160, 192, 224, 256, 288, 320, 352, 384, 416, 448), intArrayOf(0, 32, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 256, 320, 384), intArrayOf(0, 32, 40, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 256, 320)), arrayOf(intArrayOf(0, 32, 48, 56, 64, 80, 96, 112, 128, 144, 160, 176, 192, 224, 256), intArrayOf(0, 8, 16, 24, 32, 40, 48, 56, 64, 80, 96, 112, 128, 144, 160), intArrayOf(0, 8, 16, 24, 32, 40, 48, 56, 64, 80, 96, 112, 128, 144, 160)))

    /*
	 * samplingRate[verID][sampling_frequency]
	 */
    val samplingRate = arrayOf(intArrayOf(11025, 12000, 8000, 0), intArrayOf(0, 0, 0, 0), intArrayOf(22050, 24000, 16000, 0), intArrayOf(44100, 48000, 32000, 0))

    /**
     * 获取MPEG版本。
     *
     * @return MPEG版本：[.MPEG1]、 [.MPEG2] 或 [.MPEG25] 。
     */
    /*
          * verID: 2-bit
          * '00'  MPEG-2.5 (unofficial extension of MPEG 2);
          * '01'  reserved;
          * '10'  MPEG-2 (ISO/IEC 13818-3);
          * '11'  MPEG-1 (ISO/IEC 11172-3).
          */
    var version = 0
        private set

    /**
     * 获取MPEG编码层。
     *
     * @return MPEG编码层：返回值1表示LayerⅠ，2表示LayerⅡ，3表示LayerⅢ。
     */
    /*
          * layer: 2-bit
          * '11'	 Layer I
          * '10'	 Layer II
          * '01'	 Layer III
          * '00'	 reserved
          * 
          * 已换算layer=4-layer: 1--Layer I; 2--Layer II; 3--Layer III; 4--reserved
          */
    var layer = 0
        private set

    /*
	 * protection_bit: 1-bit
	 * '1'  no CRC;
	 * '0'  protected by 16 bit CRC following header.
	 */
    private var protection_bit = 0

    /**
     * 获取当前帧的位率的索引值。
     *
     * @return 当前帧的位率的索引值，位率的索引值范围是1至14的某一整数。
     */
    /* 
          * bitrate_index: 4-bit
          */
    var bitrateIndex = 0
        private set

    /**
     * 获取PCM样本采样率的索引值。
     *
     * @return PCM样本采样率的索引值。
     */
    /*
          * sampling_frequency: 2-bit
          * '00'	 44.1kHz
          * '01'	 48kHz
          * '10'	 32kHz
          * '11'  reserved
          */
    var samplingFrequency = 0
        private set
    private var padding_bit = 0

    /**
     * 获取声道模式。
     *
     * @return 声道模式，其值表示的含义：
     * <table border="1" cellpadding="8">
     * <tr><th>返回值</th><th>声道模式</th></tr>
     * <tr><td>0</td><td>立体声（stereo）</td></tr>
     * <tr><td>1</td><td>联合立体声（joint stereo）</td></tr>
     * <tr><td>2</td><td>双声道（dual channel）</td></tr>
     * <tr><td>3</td><td>单声道（mono channel）</td></tr>
    </table> *
     * @see .getModeExtension
     */
    /*
          * mode: 2-bit
          * '00'  Stereo;
          * '01'  Joint Stereo (Stereo);
          * '10'  Dual channel (Two mono channels);
          * '11'  Single channel (Mono).
          */
    var mode = 0
        private set

    /**
     * 获取声道扩展模式。
     *
     * @return 声道扩展模式，该值表示当前声道使用的立体声编码方式：
     * <table border="1" cellpadding="8">
     * <tr><th>返回值</th><th>强度立体声</th><th>中/侧立体声</th></tr>
     * <tr><td>0</td><td>off</td><td>off</td></tr>
     * <tr><td>1</td><td>on</td><td>off</td></tr>
     * <tr><td>2</td><td>off</td><td>on</td></tr>
     * <tr><td>3</td><td>on</td><td>on</td></tr>
    </table> *
     * @see .getMode
     */
    /*
          * mode_extension: 2-bit
          * 		 intensity_stereo	MS_stereo
          * '00'	 off				off
          * '01'	 on					off
          * '10'	 off				on
          * '11'	 on					on
          */
    var modeExtension = 0
        private set

    /**
     * 获取帧长度。帧的长度 = 4字节帧头 + CRC（如果有的话，2字节） + 边信息长度 + 主数据长度。
     *
     * 无论可变位率（VBR）编码的文件还是固定位率（CBR）编码的文件，每帧的长度不一定同。
     *
     * @return 当前帧的长度，单位“字节”。
     */
    var frameSize = 0
        private set

    /**
     * 获取主数据长度。
     *
     * @return 当前帧的主数据长度，单位“字节”。
     */
    var mainDataSize //main_data length
            = 0
        private set

    /**
     * 获取边信息长度。
     *
     * @return 当前帧边信息长度，单位“字节”。
     */
    var sideInfoSize //side_information length
            = 0
        private set
    private var lsf = 0
    private var headerMask = 0

    /**
     * 获取声道模式是否为中/侧立体声（Mid/Side stereo）模式。
     *
     * @return true表示是中/侧立体声模式。
     */
    var isMS = false
        private set

    /**
     * 获取声道模式是否为强度立体声（Intensity Stereo）模式。
     *
     * @return true表示是强度立体声模式。
     */
    var isIntensityStereo = false
        private set
    private var sync //true:帧头的特征未改变
            = false

    /**
     * 用指定的参数初始化Header。如果事先不知道这些参数的值，可以指定为零。
     * @param trackLength 指定音轨长度，单位“字节”。
     * @param duration 指定音轨播放时长，单位“秒”。
     */
    fun initialize(trackLength: Long, duration: Int) {
        tackLength = trackLength
        this.duration = duration.toFloat()
        headerMask = -0x200000
        progress_index = 1
        // 初始化，使可重入
        sync = false
        trackFrames = 0
        frames = 0
        tocFactor = frames
        tocPer = tocFactor
        tocNumber = tocPer
        sideInfoSize = tocNumber
        vbrtoc = null
        strBitRate = null
        progress = null
    }

    private fun parseHeader(h: Int) {
        version = h shr 19 and 3
        layer = 4 - (h shr 17) and 3
        protection_bit = h shr 16 and 0x1
        bitrateIndex = h shr 12 and 0xF
        samplingFrequency = h shr 10 and 3
        padding_bit = h shr 9 and 0x1
        mode = h shr 6 and 3
        modeExtension = h shr 4 and 3
        isMS = mode == 1 && modeExtension and 2 != 0
        isIntensityStereo = mode == 1 && modeExtension and 0x1 != 0
        lsf = if (version == MPEG1) 0 else 1
        when (layer) {
            1 -> {
                frameSize = bitrate[lsf][0][bitrateIndex] * 12000
                frameSize /= samplingRate[version][samplingFrequency]
                frameSize = frameSize + padding_bit shl 2
            }
            2 -> {
                frameSize = bitrate[lsf][1][bitrateIndex] * 144000
                frameSize /= samplingRate[version][samplingFrequency]
                frameSize += padding_bit
            }
            3 -> {
                frameSize = bitrate[lsf][2][bitrateIndex] * 144000
                frameSize /= samplingRate[version][samplingFrequency] shl lsf
                frameSize += padding_bit
                //计算帧边信息长度
                if (version == MPEG1) sideInfoSize = if (mode == 3) 17 else 32 else sideInfoSize = if (mode == 3) 9 else 17
            }
        }

        //计算主数据长度
        mainDataSize = frameSize - 4 - sideInfoSize
        if (protection_bit == 0) mainDataSize -= 2 //CRC
    }

    private fun byte2int(b: ByteArray, off: Int): Int {
        var off = off
        var int32: Int = b[off++].toInt().toInt() and 0xff
        int32 = int32 shl 8
        int32 = int32 or (b[off++].toInt().toInt() and 0xff)
        int32 = int32 shl 8
        int32 = int32 or (b[off++].toInt().toInt() and 0xff)
        int32 = int32 shl 8
        int32 = int32 or (b[off].toInt().toInt() and 0xff)
        return int32
    }

    private fun byte2short(b: ByteArray, off: Int): Int {
        var off = off
        var int16: Int = b[off++].toInt().toInt() and 0xff
        int16 = int16 shl 8
        int16 = int16 or (b[off].toInt() and 0xff)
        return int16
    }

    private fun available(h: Int, curmask: Int): Boolean {
        return h and curmask == curmask && h shr 19 and 3 != 1 // version ID:  '01' - reserved
                && h shr 17 and 3 != 0 // Layer index: '00' - reserved
                && h shr 12 and 15 != 15 // Bitrate Index: '1111' - reserved
                && h shr 12 and 15 != 0 // Bitrate Index: '0000' - free
                && h shr 10 and 3 != 3 // Sampling Rate Index: '11' - reserved
    }

    private var idx // 暂存syncFrame方法中缓冲区b的偏移量
            = 0

    fun offset(): Int {
        return idx
    }

    /**
     * 帧同步及帧头信息解码。调用前应确保源数据缓冲区 b 长度 b.length 不小于最大帧长 1732。
     *
     * 本方法执行的操作：
     *  1. 查找源数据缓冲区 b 内帧同步字（syncword）。
     *  1. 如果查找到帧同步字段： 1. 解析帧头4字节。 1. 如果当前是第一帧，解码VBR信息。
     *  1. 返回。
     *  * 若返回**true**表示查找到帧同步字段， 接下来调用 [.getVersion]、 [.getFrameSize]
     * 等方法能够返回正确的值。
     *  * 若未查找到帧同步字段，返回**false**。
     *
     * @param b
     * 源数据缓冲区。
     * @param off
     * 缓冲区 b 中数据的初始偏移量。
     * @param endPos
     * 缓冲区 b 中允许访问的最大偏移量。最大偏移量可能比缓冲区 b 的上界小。
     * @return 返回**true**表示查找到帧同步字段。
     */
    fun syncFrame(b: ByteArray, off: Int, endPos: Int): Boolean {
        var h: Int
        var mask = 0
        var skipBytes = 0 //----debug
        idx = off
        if (endPos - idx <= 4) return false
        h = byte2int(b, idx)
        idx += 4
        while (true) {
            // 1.查找帧同步字
            while (!available(h, headerMask)) {
                h = h shl 8 or (b[idx++].toInt() and 0xff)
                if (idx == endPos) {
                    idx -= 4
                    return false
                }
            }
            if (idx > 4 + off) {
                sync = false
                skipBytes += idx - off - 4
            }

            // 2. 解析帧头
            parseHeader(h)
            if (idx + frameSize > endPos + 4) {
                idx -= 4
                return false
            }

            // 若verID等帧的特征未改变(sync==true),不用与下一帧的同步头比较
            if (sync) break

            // 3.与下一帧的同步头比较,确定是否找到有效的同步字.
            if (idx + frameSize > endPos) {
                idx -= 4
                return false
            }
            mask = -0x200000 // syncword
            mask = mask or (h and 0x180000) // version ID
            mask = mask or (h and 0x60000) // Layer index
            mask = mask or (h and 0xc00) // sampling_frequency
            // mode, mode_extension 不是每帧都相同.
            if (available(byte2int(b, idx + frameSize - 4), mask)) {
                if (headerMask == -0x200000) { // 是第一帧
                    headerMask = mask
                    trackFrames = tackLength / frameSize
                    parseVBR(b, idx)
                    frameDuration = 1152f / (getSamplingRate() shl lsf)
                    if (trackFrames == 0L) trackFrames = (duration / frameDuration).toLong()
                    if (tackLength == 0L) tackLength = trackFrames * frameSize
                    duration = frameDuration * trackFrames
                }
                sync = true
                break // 找到有效的帧同步字段，结束查找
            }

            // 移动到下一字节，继续重复1-3
            h = h shl 8 or (b[idx++].toInt() and 0xff)
        }
        if (protection_bit == 0) idx += 2 // CRC word
        frames++

//		if(skipBytes > 0)
//			System.out.printf("frame# %d, skip bytes: %d\n",framecounter, skipBytes);
        return true
    }

    /**
     * 获取当前帧的位率。
     *
     * @return 当前帧的位率，单位为“千位每秒（Kbps）”。
     */
    fun getBitrate(): Int {
        return bitrate[lsf][layer - 1][bitrateIndex]
    }

    /**
     * 获取声道数。
     *
     * @return 声道数：1或2。
     */
    val channels: Int
        get() = if (mode == 3) 1 else 2

    /**
     * 获取PCM样本采样率。
     *
     * @return 获取PCM样本采样率，单位“赫兹（Hz）”
     */
    fun getSamplingRate(): Int {
        return samplingRate[version][samplingFrequency]
    }// if channels == 1

    /**
     * 获取当前帧解码后得到的PCM样本长度。通常情况下同一文件每一帧解码后得到的PCM样本长度是相同的。
     *
     * @return 当前帧解码后得到的PCM样本长度，单位“字节”。
     */
    val pcmSize: Int
        get() {
            var pcmsize = if (version == MPEG1) 4608 else 2304
            if (mode == 3) // if channels == 1
                pcmsize = pcmsize shr 1
            return pcmsize
        }

    /**
     * 获取当前文件的音轨长度。
     *
     * @return 当前文件的音轨长度，即文件长度减去一些标签（tag）信息域后的纯音乐数据的长度，单位“字节”。
     */
    // ================================ 辅助功能  =================================
    // 删除以下代码及对它们的相关引用：(1)不影响文件的正常解码；(2)不能获取及打印待解码文件的信息。
    var tackLength //音轨帧总长度(文件长度减去标签信息域长度)
            : Long = 0
        private set

    /**
     * 获取当前文件的音轨的总帧数。
     *
     * @return 当前文件的音轨的帧数，即当前文件共有多少帧。由于每一帧的播放时长相同，所以可以利用它计算文件播放时长。
     */
    var trackFrames //音轨帧数
            : Long = 0
        private set

    /**
     * 获取当前文件一帧的播放时间长度。
     *
     * @return 当前文件一帧的播放时间长度，单位“秒”。
     */
    var frameDuration //一帧时长(秒)
            = 0f
        private set

    /**
     * 获取当前文件的播放时间总长度。
     *
     * @return 当前文件的正常播放（无快进快退、数据流无损坏）时间长度，单位“秒”。
     */
    var duration //音轨播放时长(秒)
            = 0f
        private set

    /**
     * 获取当前帧的序号。
     *
     * @return 当前帧的序号，表示当前正在解码第多少帧。由于每一帧的播放时长相同，所以可以利用它计算当前播放时间进度。
     */
    var frames //当前帧序号
            = 0
        private set
    private var vbrinfo: StringBuilder? = null
    private var vbrtoc: ByteArray? = null
    private var tocNumber = 0
    private var tocPer = 0
    private var tocFactor = 0
    private var strBitRate: String? = null
    private var progress: StringBuilder? = null
    private var progress_index = 0

    /**
     * 获取播放当前文件时间进度。
     *
     * @return 当前文件的播放时间进度，单位“秒”。
     */
    val elapse: Int
        get() = (frames * frameDuration).toInt()

    /**
     * 获取可变位率（VBR）标签信息。
     * @return 可变位率（VBR）标签信息。
     */
    val vBRInfo: String?
        get() = if (vbrinfo == null) null else vbrinfo.toString()

    /*
	 * 解码存储在第一帧的VBR信息.若第一帧存储的是VBR信息,由于帧边信息被填充为0,不解 码VBR tag
	 * 而把这一帧作为音频帧解码不影响解码器的后续正常解码.
	 */
    private fun parseVBR(b: ByteArray, off: Int) {
        var off = off
        vbrinfo = null
        val maxOff = off + frameSize - 4
        if (maxOff >= b.size) return
        for (i in 2 until sideInfoSize)  //前2字节可能是CRC_word
            if (b[off + i].toInt() != 0) return
        off += sideInfoSize
        //System.out.println("tagsize=" + (frameSize-4-sideinfoSize));

        //-------------------------------VBR tag------------------------------
        if (b[off].toChar() == 'X' && b[off + 1].toChar() == 'i' && b[off + 2].toChar() == 'n' && b[off + 3].toChar() == 'g' ||
                b[off].toChar() == 'I' && b[off + 1].toChar() == 'n' && b[off + 2].toChar() == 'f' && b[off + 3].toChar() == 'o') {
            // Xing/Info header
            if (maxOff - off < 120) return
            off = xinginfoHeader(b, off)
        } else if (b[off].toChar() == 'V' && b[off + 1].toChar() == 'B' && b[off + 2].toChar() == 'R' && b[off + 3].toChar() == 'I') {
            // VBRI header
            if (maxOff - off < 26) return
            off = vbriHeader(b, off)
            val toc_size = tocNumber * tocPer
            if (maxOff - off < toc_size) return
            vbrinfo!!.append("\n          TOC: ")
            vbrinfo!!.append(tocNumber)
            vbrinfo!!.append(" * ")
            vbrinfo!!.append(tocPer)
            vbrinfo!!.append(", factor = ")
            vbrinfo!!.append(tocFactor)
            vbrtoc = ByteArray(toc_size)
            System.arraycopy(b, off, vbrtoc, 0, toc_size)
            off += toc_size
        } else return

        //-------------------------------LAME tag------------------------------
        // 36-byte: 9+1+1+8+1+1+3+1+1+2+4+2+2
        if (maxOff - off < 36 || b[off].toInt() == 0) {
            strBitRate = "VBR"
            return
        }
        //Encoder Version: 9-byte
        val encoder = String(b, off, 9)
        off += 9
        vbrinfo!!.append("\n      encoder: ")
        vbrinfo!!.append(encoder)

        //'Info Tag' revision + VBR method: 1-byte
        val revi: Int = b[off].toInt() and 0xff shr 4 //0:rev0; 1:rev1; 15:reserved
        val lame_vbr: Int = b[off++].toInt() and 0xf //0:unknown

        //低通滤波上限值(Lowpass filter value): 1-byte
        val lowpass: Int = b[off++].toInt() and 0xff
        vbrinfo!!.append("\n      lowpass: ")
        vbrinfo!!.append(lowpass * 100)
        vbrinfo!!.append("Hz")
        vbrinfo!!.append("\n     revision: ")
        vbrinfo!!.append(revi)

        //回放增益(Replay Gain):8-byte
        val peak = java.lang.Float.intBitsToFloat(byte2int(b, off)) //Peak signal amplitude
        off += 4
        val radio = byte2short(b, off) //Radio Replay Gain
        /*
		* radio:
		* bits 0h-2h: NAME of Gain adjustment:
		*	000 = not set
		*	001 = radio
		*	010 = audiophile
		* bits 3h-5h: ORIGINATOR of Gain adjustment:
		*	000 = not set
		*	001 = set by artist
		*	010 = set by user
		*	011 = set by my model
		*	100 = set by simple RMS average
		* bit 6h: Sign bit
		* bits 7h-Fh: ABSOLUTE GAIN ADJUSTMENT.
		*  storing 10x the adjustment (to give the extra decimal place).
		*/off += 2
        val phile = byte2short(b, off) //Audiophile Replay Gain
        /*
		 * phile各位含义同上(radio)
		 */off += 2

        //Encoding flags + ATH Type: 1 byte
        /*int enc_flag = (b[iOff] & 0xff) >> 4;
		int ath_type = b[iOff] & 0xf;
		//000?0000: LAME uses "--nspsytune" ?
		boolean nsp = ((enc_flag & 0x1) == 0) ? false : true;
		//00?00000: LAME uses "--nssafejoint" ?
		boolean nsj = ((enc_flag & 0x2) == 0) ? false : true;
		//0?000000: This track is --nogap continued in a next track ?
		//is true for all but the last track in a --nogap album 
		boolean nogap_next = ((enc_flag & 0x4) == 0) ? false : true;
		//?0000000: This track is the --nogap continuation of an earlier one ?
		//is true for all but the first track in a --nogap album
		boolean nogap_cont = ((enc_flag & 0x8) == 0) ? false : true;*/off++

        // ABR/CBR位率或VBR的最小位率(0xFF表示位率为255Kbps以上): 1-byte
        val lame_bitrate: Int = b[off++].toInt() and 0xff
        strBitRate = when (lame_vbr) {
            1, 8 -> String.format("CBR %1\$dK", getBitrate())
            2, 9 -> if (lame_bitrate < 0xff) String.format("ABR %1\$dK", lame_bitrate) else String.format("ABR %1\$dK以上", lame_bitrate)
            else -> if (lame_bitrate == 0) // 0: unknown is VBR ?
                "VBR" else String.format("VBR(min%dK)", lame_bitrate)
        }

        //Encoder delays: 3-byte
        off += 3

        //Misc: 1-byte
        off++

        //MP3 Gain: 1-byte. 
        //任何MP3能无损放大2^(mp3_gain/4),以1.5dB为步进值改变"Replay Gain"的3个域:
        //	"Peak signal amplitude", "Radio Replay Gain", "Audiophile Replay Gain"
        //mp3_gain = -127..+127, 对应的:
        //	分贝值-190.5dB..+190.5dB; mp3_gain增加1, 增加1.5dB
        //	放大倍数0.000000000276883..3611622602.83833951
        val mp3_gain = b[off++].toInt().toInt() //其缺省值为0
        if (mp3_gain != 0) println("    MP3 Gain: $mp3_gain [psa=$peak,rrg=$radio,arg=$phile]")

        //Preset and surround info: 2-byte
        var preset_surround = byte2short(b, off)
        val surround_info = preset_surround shr 11 and 0x7
        when (surround_info) {
            0 -> {
            }
            1 -> vbrinfo!!.append("\n     surround: DPL")
            2 -> vbrinfo!!.append("\n     surround: DPL2")
            3 -> vbrinfo!!.append("\n     surround: Ambisonic")
            7 -> vbrinfo!!.append("\n     surround: invalid data")
        }
        preset_surround = preset_surround and 0x7ff //11 bits: 2047 presets
        if (preset_surround != 0) {    //0: unknown / no preset used
            vbrinfo!!.append("\n     surround: preset ")
            vbrinfo!!.append(preset_surround)
        }
        off += 2

        //Music Length: 4-byte
        //MP3文件原始的(即除去ID3 tag,APE tag等)'LAME Tag frame'和'音乐数据'的总字节数
        val music_len = byte2int(b, off)
        off += 4
        if (music_len != 0) tackLength = music_len.toLong()

        //Music CRC: 2-byte
        off += 2

        //CRC-16 of Info Tag: 2-byte
    }

    private fun vbriHeader(b: ByteArray, off: Int): Int {
        var off = off
        if (vbrinfo == null) vbrinfo = StringBuilder()
        vbrinfo!!.append("   vbr header: vbri")

        // version ID: 2-byte
        // Delay: 2-byte
        val vbri_quality = byte2short(b, off + 8)
        vbrinfo!!.append("\n      quality: ")
        vbrinfo!!.append(vbri_quality)
        tackLength = byte2int(b, off + 10).toLong()
        vbrinfo!!.append("\n  track bytes: ")
        vbrinfo!!.append(tackLength)
        trackFrames = byte2int(b, off + 14).toLong()
        vbrinfo!!.append("\n track frames: ")
        vbrinfo!!.append(trackFrames)
        tocNumber = byte2short(b, off + 18)
        tocFactor = byte2short(b, off + 20)
        tocPer = byte2short(b, off + 22)
        val toc_frames = byte2short(b, off + 24) // 每个TOC表项的帧数
        vbrinfo!!.append("\n   toc frames: ")
        vbrinfo!!.append(toc_frames)
        off += 26
        return off
    }

    private fun xinginfoHeader(b: ByteArray, off: Int): Int {
        var off = off
        if (vbrinfo == null) vbrinfo = StringBuilder()
        vbrinfo!!.append("   vbr header: ")
        vbrinfo!!.append(String(b, off, 4))
        tackLength -= frameSize.toLong()
        val xing_flags = byte2int(b, off + 4)
        if (xing_flags and 1 == 1) { // track frames
            trackFrames = byte2int(b, off + 8).toLong()
            vbrinfo!!.append("\n track frames: ")
            vbrinfo!!.append(trackFrames)
            off += 4
        }
        off += 8 // VBR header ID + flag
        if (xing_flags and 0x2 != 0) { // track bytes
            tackLength = byte2int(b, off).toLong()
            off += 4
            vbrinfo!!.append("\n  track bytes: ")
            vbrinfo!!.append(tackLength)
        }
        if (xing_flags and 0x4 != 0) { // TOC: 100-byte
            vbrtoc = ByteArray(100)
            System.arraycopy(b, off, vbrtoc, 0, 100)
            off += 100
            //System.out.println("         TOC: yes");
        }
        if (xing_flags and 0x8 != 0) { // VBR quality
            val xing_quality = byte2int(b, off)
            off += 4
            vbrinfo!!.append("\n      quality: ")
            vbrinfo!!.append(xing_quality)
        }
        tocNumber = 100 //TOC共100个表项
        tocPer = 1 //每个表项1字节
        tocFactor = 1
        return off
    }

    /**
     * 在控制台打印帧头信息（的一部分）。
     */
    fun printHeaderInfo() {
        if (headerMask == -0x200000) //未成功解析过帧头
            return
        val duration = trackFrames * frameDuration
        val m = (duration / 60).toInt()
        val strDuration = String.format("%02d:%02d", m, (duration - m * 60 + 0.5).toInt())
        if (strBitRate == null) strBitRate = String.format("%dK", bitrate[lsf][layer - 1][bitrateIndex])
        val info = StringBuilder()
        if (version == 0) info.append("MPEG-2.5") else if (version == 2) info.append("MPEG-2") else if (version == 3) info.append("MPEG-1")
        info.append(", Layer ")
        info.append(layer)
        info.append(", ")
        info.append(getSamplingRate())
        info.append("Hz, ")
        info.append(strBitRate)
        if (mode == 0) info.append(", Stereo") else if (mode == 1) info.append(", Joint Stereo") else if (mode == 2) info.append(", Dual channel") else if (mode == 3) info.append(", Single channel(Mono)")
        if (modeExtension == 0) info.append(", ") else if (modeExtension == 1) info.append("(I/S), ") else if (modeExtension == 2) info.append("(M/S), ") else if (modeExtension == 3) info.append("(I/S & M/S), ")
        info.append(strDuration)
        println(info.toString())
    }

    /**
     * 在控制台打印可变位率（VBR）标签信息（的一部分）。
     */
    fun printVBRTag() {
        if (vbrinfo != null) println(vbrinfo.toString())
    }

    /**
     * 在控制台打印播放进度。
     */
    fun printProgress() {
        val t = frames * frameDuration
        val m = (t / 60).toInt()
        val s = t - 60 * m
        val i = ((100f * frames / trackFrames + 0.5).toInt() shl 2) / 10
        if (progress == null) progress = StringBuilder(">----------------------------------------")
        if (i == progress_index) {
            progress!!.replace(i - 1, i + 1, "=>")
            progress_index++
        }
        System.out.printf("\r#%-5d [%-41s] %02d:%05.2f ", frames, progress, m, s)
    }

    companion object {
        /**
         * MPEG版本MPEG-1。
         */
        const val MPEG1 = 3

        /**
         * MPEG版本MPEG-2。
         */
        const val MPEG2 = 2

        /**
         * MPEG版本MPEG-2.5（非官方版本）。
         */
        const val MPEG25 = 0
    }
}
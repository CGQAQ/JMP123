/*
* Layer1.java -- MPEG-1 Audio Layer I 解码
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

/**
 * 解码Layer Ⅰ。
 */
class Layer1(var header: Header, audio: IAudio?) : Layer123(header, audio) {
    var bs: BitStream
    var factor: FloatArray
    var allocation //[2][32]
            : Array<ByteArray>
    var scalefactor //[2][32]
            : Array<ByteArray>
    var syin //[2][32]
            : Array<FloatArray>

    /*
	 * 逆量化公式:
	 * s'' = (2^nb / (2^nb - 1)) * (s''' + 2^(-nb + 1))
	 * s' = factor * s''
	 */
    private fun requantization(ch: Int, sb: Int, nb: Int): Float {
        val samplecode = bs.getBits17(nb)
        val nlevels = 1 shl nb
        var requ = 2.0f * samplecode / nlevels - 1.0f //s'''
        requ += Math.pow(2.0, 1 - nb.toDouble()).toFloat()
        requ *= nlevels / (nlevels - 1).toFloat() //s''
        requ *= factor[scalefactor[ch][sb].toInt()] //s'
        return requ
    }

    override fun decodeFrame(b: ByteArray?, off: Int): Int {
        var off = off
        var sb: Int
        var gr: Int
        var ch: Int
        var nb: Int
        val nch = header.channels
        val bound = if (header.mode == 1) (header.modeExtension + 1) * 4 else 32
        val intMainDataBytes = header.mainDataSize
        if (bs.append(b, off, intMainDataBytes) < intMainDataBytes) return -1
        off += intMainDataBytes
        val maindata_begin = bs.bytePos

        //1. Bit allocation decoding
        sb = 0
        while (sb < bound) {
            ch = 0
            while (ch < nch) {
                nb = bs.getBits9(4)
                if (nb == 15) return -2
                allocation[ch][sb] = (if (nb != 0) nb + 1 else 0).toByte()
                ++ch
            }
            sb++
        }
        sb = bound
        while (sb < 32) {
            nb = bs.getBits9(4)
            if (nb == 15) return -2
            allocation[0][sb] = (if (nb != 0) nb + 1 else 0).toByte()
            sb++
        }

        //2. Scalefactor decoding
        sb = 0
        while (sb < 32) {
            ch = 0
            while (ch < nch) {
                if (allocation[ch][sb].toInt() != 0) scalefactor[ch][sb] = bs.getBits9(6).toByte()
                ch++
            }
            sb++
        }
        gr = 0
        while (gr < 12) {

            //3. Requantization of subband samples
            sb = 0
            while (sb < bound) {
                ch = 0
                while (ch < nch) {
                    nb = allocation[ch][sb].toInt()
                    if (nb == 0) syin[ch][sb] = 0F else syin[ch][sb] = requantization(ch, sb, nb)
                    ch++
                }
                sb++
            }
            //mode=1(Joint Stereo)
            sb = bound
            while (sb < 32) {
                if (allocation[0][sb].also { nb = it.toInt() }.toInt() != 0) {
                    ch = 0
                    while (ch < nch) {
                        syin[ch][sb] = requantization(ch, sb, nb)
                        ch++
                    }
                } else {
                    ch = 0
                    while (ch < nch) {
                        syin[ch][sb] = 0F
                        ch++
                    }
                }
                sb++
            }

            //4. Synthesis subband filter
            ch = 0
            while (ch < nch) {
                filter.synthesisSubBand(syin[ch], ch)
                ch++
            }
            gr++
        }

        //5. Ancillary bits
        val discard = intMainDataBytes + maindata_begin - bs.bytePos
        bs.skipBytes(discard)

        //6. output
        super.outputAudio()
        return off
    }

    /**
     * 创建一个指定头信息和音频输出的LayerⅠ帧解码器。
     *
     * @param h
     * 已经解码的帧头信息。
     * @param audio
     * 音频输出对象。
     */
    init {
        bs = BitStream(4096, 512)
        allocation = Array(2) { ByteArray(32) }
        scalefactor = Array(2) { ByteArray(32) }
        syin = Array(2) { FloatArray(32) }
        factor = Layer2.factor // ISO/IEC 11172-3 Table 3-B.1.
    }
}
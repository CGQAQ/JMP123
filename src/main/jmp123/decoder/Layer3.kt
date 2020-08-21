/*
* Layer3.java -- MPEG-1/MPEG-2 Audio Layer III (MP3) 解码
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

import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * 解码Layer Ⅲ。
 */
public class Layer3(private val header: Header, audio: IAudio?) : Layer123(header, audio) {
    @JvmField
    var granules: Int = 0
    private var channels: Int = 0
    private lateinit var bsSI // 读帧边信息(Side Information)位流
            : BitStream
    private lateinit var maindataStream: BitStreamMainData
    private var main_data_begin = 0
    private lateinit var scfsi // [channels],scale-factor selector information
            : IntArray
    private lateinit var channelInfo // [maxGr][channels]
            : Array<Array<ChannelInformation?>>
    private lateinit var sfbIndexLong: IntArray
    private lateinit var sfbIndexShort: IntArray
    private val isMPEG1: Boolean = header.version == Header.MPEG1
    private val filterCh0: SynthesisConcurrent
    private var filterCh1: SynthesisConcurrent? = null
    private var semaphore: Int

    private val lock = ReentrantLock()
    private val condition = lock.newCondition()

    //1.
    //>>>>SIDE INFORMATION (part1)=============================================
    //private int part2_3_bits;//----debug
    private fun getSideInfo(b: ByteArray, off: Int): Int {
        var ch: Int
        var gr: Int
        var ci: ChannelInformation?

        //part2_3_bits = 0;
        bsSI.feed(b, off)
        if (isMPEG1) {
            main_data_begin = bsSI.getBits9(9)
            if (channels == 1) {
                bsSI.skipBits(5) //private_bits
                scfsi[0] = bsSI.getBits9(4)
            } else {
                bsSI.skipBits(3) //private_bits
                scfsi[0] = bsSI.getBits9(4)
                scfsi[1] = bsSI.getBits9(4)
            }
            gr = 0
            while (gr < 2) {
                ch = 0
                while (ch < channels) {
                    ci = channelInfo[gr][ch]
                    ci!!.part2_3_length = bsSI.getBits17(12)
                    //part2_3_bits += ci.part2_3_length;
                    ci.big_values = bsSI.getBits9(9)
                    ci.global_gain = bsSI.getBits9(8)
                    ci.scalefac_compress = bsSI.getBits9(4)
                    ci.window_switching_flag = bsSI.get1Bit()
                    if (ci.window_switching_flag != 0) {
                        ci.block_type = bsSI.getBits9(2)
                        ci.mixed_block_flag = bsSI.get1Bit()
                        ci.table_select[0] = bsSI.getBits9(5)
                        ci.table_select[1] = bsSI.getBits9(5)
                        ci.subblock_gain[0] = bsSI.getBits9(3)
                        ci.subblock_gain[1] = bsSI.getBits9(3)
                        ci.subblock_gain[2] = bsSI.getBits9(3)
                        if (ci.block_type == 0) return -1 else if (ci.block_type == 2 && ci.mixed_block_flag == 0) ci.region0_count = 8 else ci.region0_count = 7
                        ci.region1_count = 20 - ci.region0_count
                    } else {
                        ci.table_select[0] = bsSI.getBits9(5)
                        ci.table_select[1] = bsSI.getBits9(5)
                        ci.table_select[2] = bsSI.getBits9(5)
                        ci.region0_count = bsSI.getBits9(4)
                        ci.region1_count = bsSI.getBits9(3)
                        ci.block_type = 0
                    }
                    ci.preflag = bsSI.get1Bit()
                    ci.scalefac_scale = bsSI.get1Bit()
                    ci.count1table_select = bsSI.get1Bit()
                    ch++
                }
                gr++
            }
        } else {
            // MPEG-2
            main_data_begin = bsSI.getBits9(8)
            if (channels == 1) bsSI.get1Bit() else bsSI.getBits9(2)
            ch = 0
            while (ch < channels) {
                ci = channelInfo[0][ch]
                ci!!.part2_3_length = bsSI.getBits17(12)
                //part2_3_bits += ci.part2_3_length;
                ci.big_values = bsSI.getBits9(9)
                ci.global_gain = bsSI.getBits9(8)
                ci.scalefac_compress = bsSI.getBits9(9)
                ci.window_switching_flag = bsSI.get1Bit()
                if (ci.window_switching_flag != 0) {
                    ci.block_type = bsSI.getBits9(2)
                    ci.mixed_block_flag = bsSI.get1Bit()
                    ci.table_select[0] = bsSI.getBits9(5)
                    ci.table_select[1] = bsSI.getBits9(5)
                    ci.subblock_gain[0] = bsSI.getBits9(3)
                    ci.subblock_gain[1] = bsSI.getBits9(3)
                    ci.subblock_gain[2] = bsSI.getBits9(3)
                    if (ci.block_type == 0) return -1 else if (ci.block_type == 2 && ci.mixed_block_flag == 0) ci.region0_count = 8 else {
                        ci.region0_count = 7
                        ci.region1_count = 20 - ci.region0_count
                    }
                } else {
                    ci.table_select[0] = bsSI.getBits9(5)
                    ci.table_select[1] = bsSI.getBits9(5)
                    ci.table_select[2] = bsSI.getBits9(5)
                    ci.region0_count = bsSI.getBits9(4)
                    ci.region1_count = bsSI.getBits9(3)
                    ci.block_type = 0
                    ci.mixed_block_flag = 0
                }
                ci.scalefac_scale = bsSI.get1Bit()
                ci.count1table_select = bsSI.get1Bit()
                ch++
            }
        }
        return off + header.sideInfoSize
    }

    //<<<<SIDE INFORMATION=====================================================
    //2.
    //>>>>SCALE FACTORS========================================================
    private val scalefacLong // [channels][23];
            : Array<IntArray>
    private val scalefacShort // [channels][13*3];
            : Array<IntArray>
    private lateinit var i_slen2 // MPEG-2 slen for intensity stereo
            : IntArray
    private lateinit var n_slen2 // MPEG-2 slen for 'normal' mode
            : IntArray

    // slen: 增益因子(scalefactor)比特数
    private lateinit var nr_of_sfb //[3][6][4]
            : Array<Array<ByteArray>>

    // MPEG-2
    private fun getScaleFactors_2(gr: Int, ch: Int) {
        val nr: ByteArray
        var i: Int
        var band: Int
        var slen: Int
        var num: Int
        var n = 0
        var scf = 0
        val i_stereo = header.isIntensityStereo
        val ci = channelInfo[gr][ch]
        val l = scalefacLong[ch]
        val s = scalefacShort[ch]
        rzeroBandLong = 0
        slen = if (ch > 0 && i_stereo) i_slen2[ci!!.scalefac_compress shr 1] else n_slen2[ci!!.scalefac_compress]
        ci!!.preflag = slen shr 15 and 0x1
        ci.part2_length = 0
        if (ci.block_type == 2) {
            n++
            if (ci.mixed_block_flag != 0) n++
            nr = nr_of_sfb[n][slen shr 12 and 0x7]
            i = 0
            while (i < 4) {
                num = slen and 0x7
                slen = slen shr 3
                if (num != 0) {
                    band = 0
                    while (band < nr[i]) {
                        s[scf++] = maindataStream.getBits17(num)
                        band++
                    }
                    ci.part2_length += nr[i] * num
                } else {
                    band = 0
                    while (band < nr[i]) {
                        s[scf++] = 0
                        band++
                    }
                }
                i++
            }
            n = (n shl 1) + 1
            i = 0
            while (i < n) {
                s[scf++] = 0
                i++
            }
        } else {
            nr = nr_of_sfb[n][slen shr 12 and 0x7]
            i = 0
            while (i < 4) {
                num = slen and 0x7
                slen = slen shr 3
                if (num != 0) {
                    band = 0
                    while (band < nr[i]) {
                        l[scf++] = maindataStream.getBits17(num)
                        band++
                    }
                    ci.part2_length += nr[i] * num
                } else {
                    band = 0
                    while (band < nr[i]) {
                        l[scf++] = 0
                        band++
                    }
                }
                i++
            }
            n = (n shl 1) + 1
            i = 0
            while (i < n) {
                l[scf++] = 0
                i++
            }
        }
    }

    // MPEG-1
    private val slen0 = intArrayOf(0, 0, 0, 0, 3, 1, 1, 1, 2, 2, 2, 3, 3, 3, 4, 4)
    private val slen1 = intArrayOf(0, 1, 2, 3, 0, 1, 2, 3, 1, 2, 3, 1, 2, 3, 2, 3)
    private fun getScaleFactors_1(gr: Int, ch: Int) {
        val ci = channelInfo[gr][ch]
        val len0 = slen0[ci!!.scalefac_compress]
        val len1 = slen1[ci.scalefac_compress]
        val l = scalefacLong[ch]
        val s = scalefacShort[ch]
        var scf: Int
        ci.part2_length = 0
        if (ci.window_switching_flag != 0 && ci.block_type == 2) {
            if (ci.mixed_block_flag != 0) {
                // MIXED block
                ci.part2_length = 17 * len0 + 18 * len1
                scf = 0
                while (scf < 8) {
                    l[scf] = maindataStream.getBits9(len0)
                    scf++
                }
                scf = 9
                while (scf < 18) {
                    s[scf] = maindataStream.getBits9(len0)
                    scf++
                }
                scf = 18
                while (scf < 36) {
                    s[scf] = maindataStream.getBits9(len1)
                    scf++
                }
            } else {
                // pure SHORT block
                ci.part2_length = 18 * (len0 + len1)
                scf = 0
                while (scf < 18) {
                    s[scf] = maindataStream.getBits9(len0)
                    scf++
                }
                scf = 18
                while (scf < 36) {
                    s[scf] = maindataStream.getBits9(len1)
                    scf++
                }
            }
        } else {
            // LONG types 0,1,3
            val k = scfsi[ch]
            if (gr == 0) {
                ci.part2_length = 10 * (len0 + len1) + len0
                scf = 0
                while (scf < 11) {
                    l[scf] = maindataStream.getBits9(len0)
                    scf++
                }
                scf = 11
                while (scf < 21) {
                    l[scf] = maindataStream.getBits9(len1)
                    scf++
                }
            } else {
                ci.part2_length = 0
                if (k and 8 == 0) {
                    scf = 0
                    while (scf < 6) {
                        l[scf] = maindataStream.getBits9(len0)
                        scf++
                    }
                    ci.part2_length += 6 * len0
                }
                if (k and 4 == 0) {
                    scf = 6
                    while (scf < 11) {
                        l[scf] = maindataStream.getBits9(len0)
                        scf++
                    }
                    ci.part2_length += 5 * len0
                }
                if (k and 2 == 0) {
                    scf = 11
                    while (scf < 16) {
                        l[scf] = maindataStream.getBits9(len1)
                        scf++
                    }
                    ci.part2_length += 5 * len1
                }
                if (k and 1 == 0) {
                    scf = 16
                    while (scf < 21) {
                        l[scf] = maindataStream.getBits9(len1)
                        scf++
                    }
                    ci.part2_length += 5 * len1
                }
            }
        }
    }

    //<<<<SCALE FACTORS========================================================
    //3.
    //>>>>HUFFMAN BITS=========================================================
    private val hv //[32 * 18 + 4],暂存解得的哈夫曼值
            : IntArray

    /*
	 * rzero_index[ch]: 初值为调用方法maindataStream.decodeHuff的返回值;在requantizer方法内被修正;
	 * 在hybird方法内使用.
	 */
    private val rzeroIndex = IntArray(2)
    private fun huffBits(gr: Int, ch: Int) {
        val ci = channelInfo[gr][ch]
        val r1: Int
        var r2: Int
        if (ci!!.window_switching_flag != 0) {
            val ver = header.version
            if (ver == Header.MPEG1 || ver == Header.MPEG2 && ci!!.block_type == 2) {
                ci!!.region1Start = 36
                ci.region2Start = 576
            } else {
                if (ver == Header.MPEG25) {
                    if (ci!!.block_type == 2 && ci!!.mixed_block_flag == 0) ci!!.region1Start = sfbIndexLong[6] else ci!!.region1Start = sfbIndexLong[8]
                    ci.region2Start = 576
                } else {
                    ci!!.region1Start = 54
                    ci.region2Start = 576
                }
            }
        } else {
            r1 = ci!!.region0_count + 1
            r2 = r1 + ci!!.region1_count + 1
            if (r2 > sfbIndexLong.size - 1) r2 = sfbIndexLong.size - 1
            ci.region1Start = sfbIndexLong[r1]
            ci.region2Start = sfbIndexLong[r2]
        }
        rzeroIndex[ch] = maindataStream.decodeHuff(ci, hv) // 哈夫曼解码
    }

    //<<<<HUFFMAN BITS=========================================================
    //4.
    //>>>>REQUANTIZATION & REORDER=============================================
    private var xrch0 // [maxGr][32*18]
            : Array<FloatArray>
    private lateinit var xrch1 // [maxGr][32*18]
            : Array<FloatArray>
    private val floatPow2 // [256 + 118 + 4]
            : FloatArray
    private val floatPowIS // [8207]
            : FloatArray
    private val widthLong // [22] 长块的增益因子频带(用一个增益因子逆量化频率线的条数)
            : IntArray
    private val widthShort // [13] 短块的增益因子频带
            : IntArray
    private var rzeroBandLong = 0
    private val rzeroBandShort = IntArray(3)

    // ISO/IEC 11172-3 ANNEX B,Table 3-B.6. Layer III Preemphasis
    private val pretab = intArrayOf(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 2, 2, 3, 3, 3, 2, 0)

    /**
     * 逆量化并对短块(纯短块和混合块中的短块)重排序.在逆量化时赋值的变量:<br></br>
     * rzero_bandL -- 长块非零哈夫曼值的频带数,用于强度立体声(intensity stereo)处理<br></br>
     * rzero_bandS -- 短块非零哈夫曼值的频带数,用于强度立体声处理<br></br>
     * rzero_index -- 非零哈夫曼值的"子带"数
     *
     *
     * Layer3 逆量化公式ISO/IEC 11172-3, 2.4.3.4
     *
     *
     * XRi = pow(2, 0.25 * (global_gain - 210)) <br></br>
     * if (LONG block types) <br></br>
     * 　　XRi *= pow(2, -0.5 * (1 + scalefac_scale) * (L[sfb] + preflag * pretab[sfb])) <br></br>
     * if (SHORT block types) { <br></br>
     * 　　XRi *= pow(2, 0.25 * -8 * subblock_gain[sfb]) <br></br>
     * 　　XRi *= pow(2, 0.25 * -2 * (1 + scalefac_scale) * S[scf]) } <br></br>
     * XRi *= sign(haffVal) * pow(abs(haffVal), 4/3) <br></br>
     *
     * @param gr 当前粒度。
     * @param ch 当前声道。
     * @param xrch 保存逆量化输出的576个值。
     */
    private fun requantizer(gr: Int, ch: Int, xrch: FloatArray) {
        val l = scalefacLong[ch]
        val ci = channelInfo[gr][ch]
        val preflag = ci!!.preflag == 1
        val shift = 1 + ci!!.scalefac_scale
        val maxi = rzeroIndex[ch]
        var requVal: Float
        var bi = 0
        var sfb = 0
        var width: Int
        var pre: Int
        var `val`: Int
        var hvIdx = 0
        var xri = 0
        var scf = 0
        var xriStart = 0 // 用于计算短块重排序后的下标
        var pow2i = 255 - ci!!.global_gain
        if (header.isMS) pow2i += 2 // 若声道模式为ms_stereo,要除以根2

        // pure SHORT blocks:
        // window_switching_flag=1, block_type=2, mixed_block_flag=0
        if (ci!!.window_switching_flag == 1 && ci!!.block_type == 2) {
            rzeroBandShort[2] = -1
            rzeroBandShort[1] = rzeroBandShort[2]
            rzeroBandShort[0] = rzeroBandShort[1]
            if (ci!!.mixed_block_flag == 1) {
                /*
				 * 混合块:
				 * 混合块的前8个频带是长块。 前8块各用一个增益因子逆量化，这8个增益因子 的频带总和为36，
				 * 这36条频率线用长块公式逆量化。
				 */
                rzeroBandLong = -1
                while (sfb < 8) {
                    pre = if (preflag) pretab[sfb] else 0
                    requVal = floatPow2[pow2i + (l[sfb] + pre shl shift)]
                    width = widthLong[sfb]
                    bi = 0
                    while (bi < width) {
                        `val` = hv[hvIdx] // 哈夫曼值
                        if (`val` < 0) {
                            xrch[hvIdx] = -requVal * floatPowIS[-`val`]
                            rzeroBandLong = sfb
                        } else if (`val` > 0) {
                            xrch[hvIdx] = requVal * floatPowIS[`val`]
                            rzeroBandLong = sfb
                        } else xrch[hvIdx] = 0F
                        hvIdx++
                        bi++
                    }
                    sfb++
                }

                /*
				 * 混合块的后9个频带是被加窗的短块，其每一块同一窗口内3个值的增益因子频带相同。
				 * 后9块增益因子对应的频率子带值为widthShort[3..11]
				 */rzeroBandShort[2] = 2
                rzeroBandShort[1] = rzeroBandShort[2]
                rzeroBandShort[0] = rzeroBandShort[1]
                rzeroBandLong++
                sfb = 3
                scf = 9
                xriStart = 36 // 为短块重排序准备好下标
            }

            // 短块(混合块中的短块和纯短块)
            val s = scalefacShort[ch]
            val subgain = ci!!.subblock_gain
            subgain[0] = subgain[0] shl 3
            subgain[1] = subgain[1] shl 3
            subgain[2] = subgain[2] shl 3
            var win: Int
            while (hvIdx < maxi) {
                width = widthShort[sfb]
                win = 0
                while (win < 3) {
                    requVal = floatPow2[pow2i + subgain[win] + (s[scf++] shl shift)]
                    xri = xriStart + win
                    bi = 0
                    while (bi < width) {
                        `val` = hv[hvIdx]
                        if (`val` < 0) {
                            xrch[xri] = -requVal * floatPowIS[-`val`]
                            rzeroBandShort[win] = sfb
                        } else if (`val` > 0) {
                            xrch[xri] = requVal * floatPowIS[`val`]
                            rzeroBandShort[win] = sfb
                        } else xrch[xri] = 0F
                        hvIdx++
                        xri += 3
                        bi++
                    }
                    win++
                }
                xriStart = xri - 2
                sfb++
            }
            rzeroBandShort[0]++
            rzeroBandShort[1]++
            rzeroBandShort[2]++
            rzeroBandLong++
        } else {
            // 长块
            xri = -1
            while (hvIdx < maxi) {
                pre = if (preflag) pretab[sfb] else 0
                requVal = floatPow2[pow2i + (l[sfb] + pre shl shift)]
                bi = hvIdx + widthLong[sfb]
                while (hvIdx < bi) {
                    `val` = hv[hvIdx]
                    if (`val` < 0) {
                        xrch[hvIdx] = -requVal * floatPowIS[-`val`]
                        xri = sfb
                    } else if (`val` > 0) {
                        xrch[hvIdx] = requVal * floatPowIS[`val`]
                        xri = sfb
                    } else xrch[hvIdx] = 0F
                    hvIdx++
                }
                sfb++
            }
            rzeroBandLong = xri + 1
        }

        // 不逆量化0值区,置0.
        while (hvIdx < 576) {
            xrch[hvIdx] = 0F
            hvIdx++
        }
    }

    //<<<<REQUANTIZATION & REORDER=============================================
    //5.
    //>>>>STEREO===============================================================
    // 在requantizer方法内已经作了除以根2处理,ms_stereo内不再除以根2.
    private fun ms_stereo(gr: Int) {
        val xr0 = xrch0[gr]
        val xr1 = xrch1[gr]
        val rzero_xr = if (rzeroIndex[0] > rzeroIndex[1]) rzeroIndex[0] else rzeroIndex[1]
        var xri: Int
        var tmp0: Float
        var tmp1: Float
        xri = 0
        while (xri < rzero_xr) {
            tmp0 = xr0[xri]
            tmp1 = xr1[xri]
            xr0[xri] = tmp0 + tmp1
            xr1[xri] = tmp0 - tmp1
            xri++
        }
        rzeroIndex[1] = rzero_xr
        rzeroIndex[0] = rzeroIndex[1] // ...不然可能导致声音细节丢失
    }

    private lateinit var lsf_is_coef: Array<FloatArray>
    private lateinit var is_coef: FloatArray

    // 解码一个频带强度立体声,MPEG-1
    private fun is_lines_1(pos: Int, idx0: Int, width: Int, step: Int, gr: Int) {
        var idx0 = idx0
        var xr0: Float
        for (w in width downTo 1) {
            xr0 = xrch0[gr][idx0]
            xrch0[gr][idx0] = xr0 * is_coef[pos]
            xrch1[gr][idx0] = xr0 * is_coef[6 - pos]
            idx0 += step
        }
    }

    // 解码一个频带强度立体声,MPEG-2
    private fun is_lines_2(tab2: Int, pos: Int, idx0: Int, width: Int, step: Int, gr: Int) {
        var idx0 = idx0
        var xr0: Float
        for (w in width downTo 1) {
            xr0 = xrch0[gr][idx0]
            if (pos == 0) xrch1[gr][idx0] = xr0 else {
                if (pos and 1 == 0) xrch1[gr][idx0] = xr0 * lsf_is_coef[tab2][pos - 1 shr 1] else {
                    xrch0[gr][idx0] = xr0 * lsf_is_coef[tab2][pos - 1 shr 1]
                    xrch1[gr][idx0] = xr0
                }
            }
            idx0 += step
        }
    }

    /*
	 * 强度立体声(intensity stereo)解码
	 *
	 * ISO/IEC 11172-3不对混合块中的长块作强度立体声处理,但很多MP3解码程序都作了处理.
	 */
    private fun intensity_stereo(gr: Int) {
        val ci = channelInfo[gr][1] //信息保存在右声道
        var scf: Int
        var idx: Int
        var sfb: Int
        if (channelInfo[gr][0]!!.mixed_block_flag != ci!!.mixed_block_flag
                || channelInfo[gr][0]!!.block_type != ci!!.block_type) return
        if (isMPEG1) {    //MPEG-1
            if (ci!!.block_type == 2) {
                //MPEG-1, short block/mixed block
                var w3: Int
                w3 = 0
                while (w3 < 3) {
                    sfb = rzeroBandShort[w3] // 混合块sfb最小为3
                    while (sfb < 12) {
                        idx = 3 * sfbIndexShort[sfb] + w3
                        scf = scalefacShort[1][3 * sfb + w3]
                        if (scf >= 7) {
                            sfb++
                            continue
                        }
                        is_lines_1(scf, idx, widthShort[sfb], 3, gr)
                        sfb++
                    }
                    w3++
                }
            } else {
                //MPEG-1, long block
                sfb = rzeroBandLong
                while (sfb <= 21) {
                    scf = scalefacLong[1][sfb]
                    if (scf < 7) is_lines_1(scf, sfbIndexLong[sfb], widthLong[sfb], 1, gr)
                    sfb++
                }
            }
        } else {    //MPEG-2
            val tab2 = ci!!.scalefac_compress and 0x1
            if (ci!!.block_type == 2) {
                //MPEG-2, short block/mixed block
                var w3: Int
                w3 = 0
                while (w3 < 3) {
                    sfb = rzeroBandShort[w3] // 混合块sfb最小为3
                    while (sfb < 12) {
                        idx = 3 * sfbIndexShort[sfb] + w3
                        scf = scalefacShort[1][3 * sfb + w3]
                        is_lines_2(tab2, scf, idx, widthShort[sfb], 3, gr)
                        sfb++
                    }
                    w3++
                }
            } else {
                //MPEG-2, long block
                sfb = rzeroBandLong
                while (sfb <= 21) {
                    is_lines_2(tab2, scalefacLong[1][sfb], sfbIndexLong[sfb], widthLong[sfb], 1, gr)
                    sfb++
                }
            }
        }
    }

    //<<<<STEREO===============================================================
    //6.
    //>>>>ANTIALIAS============================================================
    private fun antialias(gr: Int, ch: Int, xrch: FloatArray) {
        var i: Int
        val maxidx: Int
        var bu: Float
        var bd: Float
        maxidx = if (channelInfo[gr][ch]!!.block_type == 2) {
            if (channelInfo[gr][ch]!!.mixed_block_flag == 0) return
            18
        } else rzeroIndex[ch] - 18
        i = 0
        while (i < maxidx) {
            bu = xrch[i + 17]
            bd = xrch[i + 18]
            xrch[i + 17] = bu * 0.85749293f + bd * 0.51449576f
            xrch[i + 18] = bd * 0.85749293f - bu * 0.51449576f
            bu = xrch[i + 16]
            bd = xrch[i + 19]
            xrch[i + 16] = bu * 0.8817420f + bd * 0.47173197f
            xrch[i + 19] = bd * 0.8817420f - bu * 0.47173197f
            bu = xrch[i + 15]
            bd = xrch[i + 20]
            xrch[i + 15] = bu * 0.94962865f + bd * 0.31337745f
            xrch[i + 20] = bd * 0.94962865f - bu * 0.31337745f
            bu = xrch[i + 14]
            bd = xrch[i + 21]
            xrch[i + 14] = bu * 0.98331459f + bd * 0.18191320f
            xrch[i + 21] = bd * 0.98331459f - bu * 0.18191320f
            bu = xrch[i + 13]
            bd = xrch[i + 22]
            xrch[i + 13] = bu * 0.99551782f + bd * 0.09457419f
            xrch[i + 22] = bd * 0.99551782f - bu * 0.09457419f
            bu = xrch[i + 12]
            bd = xrch[i + 23]
            xrch[i + 12] = bu * 0.99916056f + bd * 0.04096558f
            xrch[i + 23] = bd * 0.99916056f - bu * 0.04096558f
            bu = xrch[i + 11]
            bd = xrch[i + 24]
            xrch[i + 11] = bu * 0.99989920f + bd * 0.0141986f
            xrch[i + 24] = bd * 0.99989920f - bu * 0.0141986f
            bu = xrch[i + 10]
            bd = xrch[i + 25]
            xrch[i + 10] = bu * 0.99999316f + bd * 3.69997467e-3f
            xrch[i + 25] = bd * 0.99999316f - bu * 3.69997467e-3f
            i += 18
        }
    }

    //<<<<ANTIALIAS============================================================
    //7.
    //>>>>HYBRID(synthesize via iMDCT)=========================================
    private val imdctWin = arrayOf(floatArrayOf(0.0322824f, 0.1072064f, 0.2014143f, 0.3256164f, 0.5f, 0.7677747f,
            1.2412229f, 2.3319514f, 7.7441506f, -8.4512568f, -3.0390580f, -1.9483297f,
            -1.4748814f, -1.2071068f, -1.0327232f, -0.9085211f, -0.8143131f, -0.7393892f,
            -0.6775254f, -0.6248445f, -0.5787917f, -0.5376016f, -0.5f, -0.4650284f,
            -0.4319343f, -0.4000996f, -0.3689899f, -0.3381170f, -0.3070072f, -0.2751725f,
            -0.2420785f, -0.2071068f, -0.1695052f, -0.1283151f, -0.0822624f, -0.0295815f), floatArrayOf(0.0322824f, 0.1072064f, 0.2014143f, 0.3256164f, 0.5f, 0.7677747f,
            1.2412229f, 2.3319514f, 7.7441506f, -8.4512568f, -3.0390580f, -1.9483297f,
            -1.4748814f, -1.2071068f, -1.0327232f, -0.9085211f, -0.8143131f, -0.7393892f,
            -0.6781709f, -0.6302362f, -0.5928445f, -0.5636910f, -0.5411961f, -0.5242646f,
            -0.5077583f, -0.4659258f, -0.3970546f, -0.3046707f, -0.1929928f, -0.0668476f,
            -0.0f, -0.0f, -0.0f, -0.0f, -0.0f, -0.0f), floatArrayOf(), floatArrayOf(0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f,
            0.3015303f, 1.4659259f, 6.9781060f, -9.0940447f, -3.5390582f, -2.2903500f,
            -1.6627548f, -1.3065630f, -1.0828403f, -0.9305795f, -0.8213398f, -0.7400936f,
            -0.6775254f, -0.6248445f, -0.5787917f, -0.5376016f, -0.5f, -0.4650284f,
            -0.4319343f, -0.4000996f, -0.3689899f, -0.3381170f, -0.3070072f, -0.2751725f,
            -0.2420785f, -0.2071068f, -0.1695052f, -0.1283151f, -0.0822624f, -0.0295815f))

    private fun imdct12(xrch: FloatArray, pre: FloatArray, off: Int) {
        var i: Int
        var j: Int
        var in1: Float
        var in2: Float
        var in3: Float
        var in4: Float
        var out0: Float
        var out1: Float
        var out2: Float
        var out3: Float
        var out4: Float
        var out5: Float
        var tmp: Float
        var out6 = 0f
        var out7 = 0f
        var out8 = 0f
        var out9 = 0f
        var out10 = 0f
        var out11 = 0f
        var out12 = 0f
        var out13 = 0f
        var out14 = 0f
        var out15 = 0f
        var out16 = 0f
        var out17 = 0f
        var f0 = 0f
        var f1 = 0f
        var f2 = 0f
        var f3 = 0f
        var f4 = 0f
        var f5 = 0f
        j = 0
        while (j != 3) {
            i = j + off
            //>>>>>>>>>>>> 12-point IMDCT
            //>>>>>> 6-point IDCT
            xrch[15 + i] += xrch[9 + i].let { xrch[12 + i] += it; xrch[12 + i] } + xrch[6 + i]
            xrch[9 + i] += xrch[3 + i].let { xrch[6 + i] += it; xrch[6 + i] } + xrch[i]
            xrch[3 + i] += xrch[i]

            //>>> 3-point IDCT on even
            out1 = xrch[i].also { in1 = it } - xrch[12 + i].also { in2 = it }
            in3 = in1 + in2 * 0.5f
            in4 = xrch[6 + i] * 0.8660254f
            out0 = in3 + in4
            out2 = in3 - in4
            //<<< End 3-point IDCT on even

            //>>> 3-point IDCT on odd (for 6-point IDCT)
            out4 = (xrch[3 + i].also { in1 = it } - xrch[15 + i].also { in2 = it }) * 0.7071068f
            in3 = in1 + in2 * 0.5f
            in4 = xrch[9 + i] * 0.8660254f
            out5 = (in3 + in4) * 0.5176381f
            out3 = (in3 - in4) * 1.9318516f
            //<<< End 3-point IDCT on odd

            // Output: butterflies on 2,3-point IDCT's (for 6-point IDCT)
            tmp = out0
            out0 += out5
            out5 = tmp - out5
            tmp = out1
            out1 += out4
            out4 = tmp - out4
            tmp = out2
            out2 += out3
            out3 = tmp - out3
            //<<<<<< End 6-point IDCT
            //<<<<<<<<<<<< End 12-point IDCT
            tmp = out3 * 0.1072064f
            when (j) {
                0 -> {
                    out6 = tmp
                    out7 = out4 * 0.5f
                    out8 = out5 * 2.3319512f
                    out9 = -out5 * 3.0390580f
                    out10 = -out4 * 1.2071068f
                    out11 = -tmp * 7.5957541f
                    f0 = out2 * 0.6248445f
                    f1 = out1 * 0.5f
                    f2 = out0 * 0.4000996f
                    f3 = out0 * 0.3070072f
                    f4 = out1 * 0.2071068f
                    f5 = out2 * 0.0822623f
                }
                1 -> {
                    out12 = tmp - f0
                    out13 = out4 * 0.5f - f1
                    out14 = out5 * 2.3319512f - f2
                    out15 = -out5 * 3.0390580f - f3
                    out16 = -out4 * 1.2071068f - f4
                    out17 = -tmp * 7.5957541f - f5
                    f0 = out2 * 0.6248445f
                    f1 = out1 * 0.5f
                    f2 = out0 * 0.4000996f
                    f3 = out0 * 0.3070072f
                    f4 = out1 * 0.2071068f
                    f5 = out2 * 0.0822623f
                }
                2 -> {
                    // output
                    i = off
                    xrch[i + 0] = pre[i + 0]
                    xrch[i + 1] = pre[i + 1]
                    xrch[i + 2] = pre[i + 2]
                    xrch[i + 3] = pre[i + 3]
                    xrch[i + 4] = pre[i + 4]
                    xrch[i + 5] = pre[i + 5]
                    xrch[i + 6] = pre[i + 6] + out6
                    xrch[i + 7] = pre[i + 7] + out7
                    xrch[i + 8] = pre[i + 8] + out8
                    xrch[i + 9] = pre[i + 9] + out9
                    xrch[i + 10] = pre[i + 10] + out10
                    xrch[i + 11] = pre[i + 11] + out11
                    xrch[i + 12] = pre[i + 12] + out12
                    xrch[i + 13] = pre[i + 13] + out13
                    xrch[i + 14] = pre[i + 14] + out14
                    xrch[i + 15] = pre[i + 15] + out15
                    xrch[i + 16] = pre[i + 16] + out16
                    xrch[i + 17] = pre[i + 17] + out17
                    pre[i + 0] = tmp - f0
                    pre[i + 1] = out4 * 0.5f - f1
                    pre[i + 2] = out5 * 2.3319512f - f2
                    pre[i + 3] = -out5 * 3.0390580f - f3
                    pre[i + 4] = -out4 * 1.2071068f - f4
                    pre[i + 5] = -tmp * 7.5957541f - f5
                    pre[i + 6] = -out2 * 0.6248445f
                    pre[i + 7] = -out1 * 0.5f
                    pre[i + 8] = -out0 * 0.4000996f
                    pre[i + 9] = -out0 * 0.3070072f
                    pre[i + 10] = -out1 * 0.2071068f
                    pre[i + 11] = -out2 * 0.0822623f
                    run {
                        pre[i + 14] = 0F
                        pre[i + 13] = pre[i + 14]
                        pre[i + 12] = pre[i + 13]
                    }
                    run {
                        pre[i + 17] = 0F
                        pre[i + 16] = pre[i + 17]
                        pre[i + 15] = pre[i + 16]
                    }
                }
            }
            j++
        }
    }

    private fun imdct36(xrch: FloatArray, preBlck: FloatArray, off: Int, block_type: Int) {
        var in0: Float
        var in1: Float
        var in2: Float
        var in3: Float
        var in4: Float
        var in5: Float
        var in6: Float
        var in7: Float
        var in8: Float
        var in9: Float
        var in10: Float
        var in11: Float
        var in12: Float
        var in13: Float
        var in14: Float
        var in15: Float
        var in16: Float
        var in17: Float
        var out0: Float
        var out1: Float
        var out2: Float
        var out3: Float
        var out4: Float
        var out5: Float
        var out6: Float
        var out7: Float
        var out8: Float
        var out9: Float
        var out10: Float
        var out11: Float
        var out12: Float
        var out13: Float
        var out14: Float
        var out15: Float
        var out16: Float
        var out17: Float
        var tmp: Float

        //>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>> 36-point IDCT
        //>>>>>>>>>>>>>>>>>> 18-point IDCT for odd
        xrch[off + 17] += xrch[off + 15].let { xrch[off + 16] += it; xrch[off + 16] } + xrch[off + 14]
        xrch[off + 15] += xrch[off + 13].let { xrch[off + 14] += it; xrch[off + 14] } + xrch[off + 12]
        xrch[off + 13] += xrch[off + 11].let { xrch[off + 12] += it; xrch[off + 12] } + xrch[off + 10]
        xrch[off + 11] += xrch[off + 9].let { xrch[off + 10] += it; xrch[off + 10] } + xrch[off + 8]
        xrch[off + 9] += xrch[off + 7].let { xrch[off + 8] += it; xrch[off + 8] } + xrch[off + 6]
        xrch[off + 7] += xrch[off + 5].let { xrch[off + 6] += it; xrch[off + 6] } + xrch[off + 4]
        xrch[off + 5] += xrch[off + 3].let { xrch[off + 4] += it; xrch[off + 4] } + xrch[off + 2]
        xrch[off + 3] += xrch[off + 1].let { xrch[off + 2] += it; xrch[off + 2] } + xrch[off + 0]
        xrch[off + 1] += xrch[off + 0]

        //>>>>>>>>> 9-point IDCT on even
        /*
		 *  for(m = 0; m < 9; m++) {
		 *      sum = 0;
		 *      for(n = 0; n < 18; n += 2)
		 *          sum += in[n] * cos(PI36 * (2 * m + 1) * n);
		 *      out18[m] = sum;
		 *  }
		 */in0 = xrch[off + 0] + xrch[off + 12] * 0.5f
        in1 = xrch[off + 0] - xrch[off + 12]
        in2 = xrch[off + 8] + xrch[off + 16] - xrch[off + 4]
        out4 = in1 + in2
        in3 = in1 - in2 * 0.5f
        in4 = (xrch[off + 10] + xrch[off + 14] - xrch[off + 2]) * 0.8660254f // cos(PI/6)
        out1 = in3 - in4
        out7 = in3 + in4
        in5 = (xrch[off + 4] + xrch[off + 8]) * 0.9396926f //cos( PI/9)
        in6 = (xrch[off + 16] - xrch[off + 8]) * 0.1736482f //cos(4PI/9)
        in7 = -(xrch[off + 4] + xrch[off + 16]) * 0.7660444f //cos(2PI/9)
        in17 = in0 - in5 - in7
        in8 = in5 + in0 + in6
        in9 = in0 + in7 - in6
        in12 = xrch[off + 6] * 0.8660254f //cos(PI/6)
        in10 = (xrch[off + 2] + xrch[off + 10]) * 0.9848078f //cos(PI/18)
        in11 = (xrch[off + 14] - xrch[off + 10]) * 0.3420201f //cos(7PI/18)
        in13 = in10 + in11 + in12
        out0 = in8 + in13
        out8 = in8 - in13
        in14 = -(xrch[off + 2] + xrch[off + 14]) * 0.6427876f //cos(5PI/18)
        in15 = in10 + in14 - in12
        in16 = in11 - in14 - in12
        out3 = in9 + in15
        out5 = in9 - in15
        out2 = in17 + in16
        out6 = in17 - in16
        //<<<<<<<<< End 9-point IDCT on even

        //>>>>>>>>> 9-point IDCT on odd
        /* 
		 *  for(m = 0; m < 9; m++) {
		 *      sum = 0;
		 *      for(n = 0;n < 18; n += 2)
		 *          sum += in[n + 1] * cos(PI36 * (2 * m + 1) * n);
		 *      out18[17-m] = sum;
		 * }
		 */in0 = xrch[off + 1] + xrch[off + 13] * 0.5f //cos(PI/3)
        in1 = xrch[off + 1] - xrch[off + 13]
        in2 = xrch[off + 9] + xrch[off + 17] - xrch[off + 5]
        out13 = (in1 + in2) * 0.7071068f //cos(PI/4)
        in3 = in1 - in2 * 0.5f
        in4 = (xrch[off + 11] + xrch[off + 15] - xrch[off + 3]) * 0.8660254f //cos(PI/6)
        out16 = (in3 - in4) * 0.5176381f // 0.5/cos( PI/12)
        out10 = (in3 + in4) * 1.9318517f // 0.5/cos(5PI/12)
        in5 = (xrch[off + 5] + xrch[off + 9]) * 0.9396926f // cos( PI/9)
        in6 = (xrch[off + 17] - xrch[off + 9]) * 0.1736482f // cos(4PI/9)
        in7 = -(xrch[off + 5] + xrch[off + 17]) * 0.7660444f // cos(2PI/9)
        in17 = in0 - in5 - in7
        in8 = in5 + in0 + in6
        in9 = in0 + in7 - in6
        in12 = xrch[off + 7] * 0.8660254f // cos(PI/6)
        in10 = (xrch[off + 3] + xrch[off + 11]) * 0.9848078f // cos(PI/18)
        in11 = (xrch[off + 15] - xrch[off + 11]) * 0.3420201f // cos(7PI/18)
        in13 = in10 + in11 + in12
        out17 = (in8 + in13) * 0.5019099f // 0.5/cos(PI/36)
        out9 = (in8 - in13) * 5.7368566f // 0.5/cos(17PI/36)
        in14 = -(xrch[off + 3] + xrch[off + 15]) * 0.6427876f // cos(5PI/18)
        in15 = in10 + in14 - in12
        in16 = in11 - in14 - in12
        out14 = (in9 + in15) * 0.6103873f // 0.5/cos(7PI/36)
        out12 = (in9 - in15) * 0.8717234f // 0.5/cos(11PI/36)
        out15 = (in17 + in16) * 0.5516890f // 0.5/cos(5PI/36)
        out11 = (in17 - in16) * 1.1831008f // 0.5/cos(13PI/36)
        //<<<<<<<<< End. 9-point IDCT on odd

        // Butterflies on 9-point IDCT's
        tmp = out0
        out0 += out17
        out17 = tmp - out17
        tmp = out1
        out1 += out16
        out16 = tmp - out16
        tmp = out2
        out2 += out15
        out15 = tmp - out15
        tmp = out3
        out3 += out14
        out14 = tmp - out14
        tmp = out4
        out4 += out13
        out13 = tmp - out13
        tmp = out5
        out5 += out12
        out12 = tmp - out12
        tmp = out6
        out6 += out11
        out11 = tmp - out11
        tmp = out7
        out7 += out10
        out10 = tmp - out10
        tmp = out8
        out8 += out9
        out9 = tmp - out9
        //<<<<<<<<<<<<<<<<<< End of 18-point IDCT
        //<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<< End of 36-point IDCT

        // output
        val win = imdctWin[block_type]
        xrch[off + 0] = preBlck[off + 0] + out9 * win[0]
        xrch[off + 1] = preBlck[off + 1] + out10 * win[1]
        xrch[off + 2] = preBlck[off + 2] + out11 * win[2]
        xrch[off + 3] = preBlck[off + 3] + out12 * win[3]
        xrch[off + 4] = preBlck[off + 4] + out13 * win[4]
        xrch[off + 5] = preBlck[off + 5] + out14 * win[5]
        xrch[off + 6] = preBlck[off + 6] + out15 * win[6]
        xrch[off + 7] = preBlck[off + 7] + out16 * win[7]
        xrch[off + 8] = preBlck[off + 8] + out17 * win[8]
        xrch[off + 9] = preBlck[off + 9] + out17 * win[9]
        xrch[off + 10] = preBlck[off + 10] + out16 * win[10]
        xrch[off + 11] = preBlck[off + 11] + out15 * win[11]
        xrch[off + 12] = preBlck[off + 12] + out14 * win[12]
        xrch[off + 13] = preBlck[off + 13] + out13 * win[13]
        xrch[off + 14] = preBlck[off + 14] + out12 * win[14]
        xrch[off + 15] = preBlck[off + 15] + out11 * win[15]
        xrch[off + 16] = preBlck[off + 16] + out10 * win[16]
        xrch[off + 17] = preBlck[off + 17] + out9 * win[17]
        preBlck[off + 0] = out8 * win[18]
        preBlck[off + 1] = out7 * win[19]
        preBlck[off + 2] = out6 * win[20]
        preBlck[off + 3] = out5 * win[21]
        preBlck[off + 4] = out4 * win[22]
        preBlck[off + 5] = out3 * win[23]
        preBlck[off + 6] = out2 * win[24]
        preBlck[off + 7] = out1 * win[25]
        preBlck[off + 8] = out0 * win[26]
        preBlck[off + 9] = out0 * win[27]
        preBlck[off + 10] = out1 * win[28]
        preBlck[off + 11] = out2 * win[29]
        preBlck[off + 12] = out3 * win[30]
        preBlck[off + 13] = out4 * win[31]
        preBlck[off + 14] = out5 * win[32]
        preBlck[off + 15] = out6 * win[33]
        preBlck[off + 16] = out7 * win[34]
        preBlck[off + 17] = out8 * win[35]
    }

    private val preBlckCh0 // [32*18],左声道FIFO队列
            : FloatArray
    private lateinit var preBlckCh1 // [32*18],右声道FIFO
            : FloatArray

    private fun hybrid(gr: Int, ch: Int, xrch: FloatArray, preb: FloatArray) {
        val ci = channelInfo[gr][ch]
        val maxi = rzeroIndex[ch]
        var i: Int
        var block_type: Int
        i = 0
        while (i < maxi) {
            block_type = if (ci!!.window_switching_flag != 0
                    && ci!!.mixed_block_flag != 0 && i < 36) 0 else ci!!.block_type
            if (block_type == 2) imdct12(xrch, preb, i) else imdct36(xrch, preb, i, block_type)
            i += 18
        }

        // 0值区
        while (i < 576) {
            xrch[i] = preb[i]
            preb[i] = 0F
            i++
        }
    }
    //<<<<HYBRID(synthesize via iMDCT)=========================================
    //8.
    //>>>>INVERSE QUANTIZE SAMPLES=============================================
    //
    // 在decoder.ConcurrentSynthesis.run 方法内实现多相频率倒置
    //
    //<<<<INVERSE QUANTIZE SAMPLES=============================================
    //9.
    //>>>>SYNTHESIZE VIA POLYPHASE MDCT========================================
    //
    // 在decoder.ConcurrentSynthesis.run()方法内调用filter.synthesisSubBand()
    // 实现多相合成滤波
    //
    //<<<<SYNTHESIZE VIA POLYPHASE MDCT========================================
    //10.
    //>>>>OUTPUT PCM SAMPLES===================================================
    //
    // jmp123.decoder.AudioBuffer, jmp123.output.Audio
    //
    //<<<<OUTPUT PCM SAMPLES===================================================
    /**
     * 解码1帧Layer Ⅲ
     */
    override fun decodeFrame(b: ByteArray, off: Int): Int {
        /*
		 * part1 : side information
		 */
        var off = off
        var gr: Int
        val i = getSideInfo(b, off)
        if (i < 0) return off + header.frameSize - 4 // 跳过这一帧
        off = i

        /*
		 * part2_3: scale factors + huffman bits
		 * length: ((part2_3_bits + 7) >> 3) bytes
		 */
        val maindataSize = header.mainDataSize
        val bufSize = maindataStream.size
        if (bufSize < main_data_begin) {
            // 若出错，不解码当前这一帧， 将主数据(main_data)填入位流缓冲区后返回，
            // 在解码下一帧时全部或部分利用填入的这些主数据。
            maindataStream.append(b, off, maindataSize)
            return off + maindataSize
        }

        // 丢弃上一帧的填充位
        val discard = bufSize - maindataStream.bytePos - main_data_begin
        maindataStream.skipBytes(discard)

        // 主数据添加到位流缓冲区
        maindataStream.append(b, off, maindataSize)
        off += maindataSize
        //maindataStream.mark();//----debug
        gr = 0
        while (gr < granules) {
            if (isMPEG1) getScaleFactors_1(gr, 0) else getScaleFactors_2(gr, 0)
            huffBits(gr, 0)
            requantizer(gr, 0, xrch0[gr])
            if (channels == 2) {
                if (isMPEG1) getScaleFactors_1(gr, 1) else getScaleFactors_2(gr, 1)
                huffBits(gr, 1)
                requantizer(gr, 1, xrch1[gr])
                if (header.isMS) ms_stereo(gr)
                if (header.isIntensityStereo) intensity_stereo(gr)
            }
            antialias(gr, 0, xrch0[gr])
            hybrid(gr, 0, xrch0[gr], preBlckCh0)
            if (channels == 2) {
                antialias(gr, 1, xrch1[gr])
                hybrid(gr, 1, xrch1[gr], preBlckCh1)
            }
            gr++
        }
        // int part2_3_bytes = maindataStream.getMark();//----debug
        // 可以在这调用maindataStream.skipBits(part2_3_bits & 7)丢弃填充位，
        // 更好的方法是放在解码下一帧主数据之前处理，如果位流错误，可以顺便纠正。
        try {
            lock.withLock {
                while (semaphore < channels) // 等待上一帧channels个声道完成多相合成滤波
                    condition.await()
                semaphore = 0 // 信号量置0
            }
        } catch (e: InterruptedException) {
            close()
            return off
        }

        //实现播放
        outputAudio()

        // 异步多相合成滤波
        xrch0 = filterCh0.startSynthesis()
        if (channels == 2) xrch1 = filterCh1!!.startSynthesis()
        return off
    }

    /**
     * 关闭帧的解码。如果用多线程并发解码，这些并发的解码线程将被终止。
     * @see Layer123.close
     */
    @Synchronized
    override fun close() {
        try {
            semaphore = channels
            lock.withLock {
                condition.signal()
            }
        } catch (r: IllegalMonitorStateException) {
            print(r)
        }
        super.close()
        filterCh0.shutdown()
        if (channels == 2) filterCh1!!.shutdown()
    }

    /**
     * 滤波线程完成一次的滤波任务后向调用者提交结果。滤波线程完成一次滤波任务后调用该方法。
     */
    @Synchronized
    fun submitSynthesis() {
        if (++semaphore == channels) lock.withLock { condition.signal() }
    }

    /**
     * 一个粒度内一个声道的信息。哈夫曼解码用到part2_3_length等protected变量。
     */


    /**
     * 创建一个指定头信息和音频输出的 Layer Ⅲ 帧解码器。
     *
     * @param h
     * 已经解码的帧头信息。
     * @param audio
     * 音频输出对象。
     */
    init {
        granules = if (isMPEG1) 2 else 1
        channels = header.channels
        semaphore = channels
        maindataStream = BitStreamMainData(4096, 512)
        scfsi = IntArray(channels)
        bsSI = BitStream(0, 0)
        scalefacLong = Array(channels) { IntArray(23) }
        scalefacShort = Array(channels) { IntArray(3 * 13) }
        hv = IntArray(32 * 18 + 4)
        widthLong = IntArray(22)
        widthShort = IntArray(13)
        channelInfo = Array(granules) { arrayOfNulls(channels) }
        for (gr in 0 until granules) for (ch in 0 until channels) channelInfo[gr][ch] = ChannelInformation()
        filterCh0 = SynthesisConcurrent(this, 0) //ch=0
        Thread(filterCh0, "synthesis_left").start()
        xrch0 = filterCh0.buffer
        preBlckCh0 = FloatArray(32 * 18)
        if (channels == 2) {
            filterCh1 = SynthesisConcurrent(this, 1) //ch=1
            Thread(filterCh1, "synthesis_right").start()
            xrch1 = filterCh1!!.getBuffer()
            preBlckCh1 = FloatArray(32 * 18)
        }
        var i: Int

        // 用于查表求 v^(4/3)，v是经哈夫曼解码出的一个(正)值，该值的范围是0..8191
        floatPowIS = FloatArray(8207)
        i = 0
        while (i < 8207) {
            floatPowIS[i] = Math.pow(i.toDouble(), 4.0 / 3.0).toFloat()
            i++
        }

        // 用于查表求 2^(-0.25 * i)
        // 按公式短块时i最大值: 210 - 0   + 8 * 7 + 4 * 15 + 2 = 328
        // 长块或短块时i最小值: 210 - 255 + 0     + 0      + 0 = -45
        // 查表法时下标范围为0..328+45.
        floatPow2 = FloatArray(328 + 46)
        i = 0
        while (i < 374) {
            floatPow2[i] = Math.pow(2.0, -0.25 * (i - 45)).toFloat()
            i++
        }

        //---------------------------------------------------------------------
        //待解码文件的不同特征用到不同的变量.初始化:
        //---------------------------------------------------------------------
        var sfreq = header.samplingFrequency
        sfreq += if (isMPEG1) 0 else if (header.version == Header.MPEG2) 3 else 6
        when (sfreq) {
            0 -> {
                // MPEG-1, sampling_frequency=0, 44.1kHz
                sfbIndexLong = intArrayOf(0, 4, 8, 12, 16, 20, 24, 30, 36, 44,
                        52, 62, 74, 90, 110, 134, 162, 196, 238, 288, 342, 418, 576)
                sfbIndexShort = intArrayOf(0, 4, 8, 12, 16, 22, 30, 40, 52, 66,
                        84, 106, 136, 192)
            }
            1 -> {
                // MPEG-1, sampling_frequency=1, 48kHz
                sfbIndexLong = intArrayOf(0, 4, 8, 12, 16, 20, 24, 30, 36, 42,
                        50, 60, 72, 88, 106, 128, 156, 190, 230, 276, 330, 384, 576)
                sfbIndexShort = intArrayOf(0, 4, 8, 12, 16, 22, 28, 38, 50, 64,
                        80, 100, 126, 192)
            }
            2 -> {
                // MPEG-1, sampling_frequency=2, 32kHz
                sfbIndexLong = intArrayOf(0, 4, 8, 12, 16, 20, 24, 30, 36, 44,
                        54, 66, 82, 102, 126, 156, 194, 240, 296, 364, 448, 550, 576)
                sfbIndexShort = intArrayOf(0, 4, 8, 12, 16, 22, 30, 42, 58, 78,
                        104, 138, 180, 192)
            }
            3 -> {
                // MPEG-2, sampling_frequency=0, 22.05kHz
                sfbIndexLong = intArrayOf(0, 6, 12, 18, 24, 30, 36, 44, 54, 66,
                        80, 96, 116, 140, 168, 200, 238, 284, 336, 396, 464, 522, 576)
                sfbIndexShort = intArrayOf(0, 4, 8, 12, 18, 24, 32, 42, 56, 74,
                        100, 132, 174, 192)
            }
            4 -> {
                // MPEG-2, sampling_frequency=1, 24kHz
                sfbIndexLong = intArrayOf(0, 6, 12, 18, 24, 30, 36, 44, 54, 66,
                        80, 96, 114, 136, 162, 194, 232, 278, 330, 394, 464, 540, 576)
                sfbIndexShort = intArrayOf(0, 4, 8, 12, 18, 26, 36, 48, 62, 80,
                        104, 136, 180, 192)
            }
            5 -> {
                // MPEG-2, sampling_frequency=2, 16kHz
                sfbIndexLong = intArrayOf(0, 6, 12, 18, 24, 30, 36, 44, 54, 66,
                        80, 96, 116, 140, 168, 200, 238, 284, 336, 396, 464, 522, 576)
                sfbIndexShort = intArrayOf(0, 4, 8, 12, 18, 26, 36, 48, 62, 80,
                        104, 134, 174, 192)
            }
            6 -> {
                // MPEG-2.5, sampling_frequency=0, 11.025kHz
                sfbIndexLong = intArrayOf(0, 6, 12, 18, 24, 30, 36, 44, 54, 66,
                        80, 96, 116, 140, 168, 200, 238, 284, 336, 396, 464, 522, 576)
                sfbIndexShort = intArrayOf(0, 4, 8, 12, 18, 26, 36, 48, 62, 80,
                        104, 134, 174, 192)
            }
            7 -> {
                // MPEG-2.5, sampling_frequency=1, 12kHz
                sfbIndexLong = intArrayOf(0, 6, 12, 18, 24, 30, 36, 44, 54, 66,
                        80, 96, 116, 140, 168, 200, 238, 284, 336, 396, 464, 522, 576)
                sfbIndexShort = intArrayOf(0, 4, 8, 12, 18, 26, 36, 48, 62, 80,
                        104, 134, 174, 192)
            }
            8 -> {
                // MPEG-2.5, sampling_frequency=2, 8kHz
                sfbIndexLong = intArrayOf(0, 12, 24, 36, 48, 60, 72, 88, 108, 132,
                        160, 192, 232, 280, 336, 400, 476, 566, 568, 570, 572, 574, 576)
                sfbIndexShort = intArrayOf(0, 8, 16, 24, 36, 52, 72, 96, 124,
                        160, 162, 164, 166, 192)
            }
        }
        i = 0
        while (i < 22) {
            widthLong[i] = sfbIndexLong[i + 1] - sfbIndexLong[i]
            i++
        }
        i = 0
        while (i < 13) {
            widthShort[i] = sfbIndexShort[i + 1] - sfbIndexShort[i]
            i++
        }
        //-----------------------------------------------------------------
        if (isMPEG1) {
            // MPEG-1, intensity_stereo
            is_coef = floatArrayOf(0.0f, 0.211324865f, 0.366025404f, 0.5f,
                    0.633974596f, 0.788675135f, 1.0f)
        } else {
            // MPEG-2, intensity_stereo
            lsf_is_coef = arrayOf(floatArrayOf(0.840896415f, 0.707106781f, 0.594603558f, 0.5f, 0.420448208f,
                    0.353553391f, 0.297301779f, 0.25f, 0.210224104f, 0.176776695f,
                    0.148650889f, 0.125f, 0.105112052f, 0.088388348f, 0.074325445f), floatArrayOf(0.707106781f, 0.5f, 0.353553391f, 0.25f, 0.176776695f, 0.125f,
                    0.088388348f, 0.0625f, 0.044194174f, 0.03125f, 0.022097087f,
                    0.015625f, 0.011048543f, 0.0078125f, 0.005524272f))
            i_slen2 = IntArray(256) // MPEG-2 slen for intensity_stereo
            n_slen2 = IntArray(512) // MPEG-2 slen for normal mode
            nr_of_sfb = arrayOf(arrayOf(byteArrayOf(6, 5, 5, 5), byteArrayOf(6, 5, 7, 3), byteArrayOf(11, 10, 0, 0), byteArrayOf(7, 7, 7, 0), byteArrayOf(6, 6, 6, 3), byteArrayOf(8, 8, 5, 0)), arrayOf(byteArrayOf(9, 9, 9, 9), byteArrayOf(9, 9, 12, 6), byteArrayOf(18, 18, 0, 0), byteArrayOf(12, 12, 12, 0), byteArrayOf(12, 9, 9, 6), byteArrayOf(15, 12, 9, 0)), arrayOf(byteArrayOf(6, 9, 9, 9), byteArrayOf(6, 9, 12, 6), byteArrayOf(15, 18, 0, 0), byteArrayOf(6, 15, 12, 0), byteArrayOf(6, 12, 9, 6), byteArrayOf(6, 18, 9, 0)))

            // ISO/IEC 13818-3 subclause 2.4.3.2 slenx, x=1..4
            var j: Int
            var k: Int
            var l: Int
            var n: Int
            i = 0
            while (i < 5) {
                j = 0
                while (j < 6) {
                    k = 0
                    while (k < 6) {
                        n = k + j * 6 + i * 36
                        i_slen2[n] = i or (j shl 3) or (k shl 6) or (3 shl 12)
                        k++
                    }
                    j++
                }
                i++
            }
            i = 0
            while (i < 4) {
                j = 0
                while (j < 4) {
                    k = 0
                    while (k < 4) {
                        n = k + (j shl 2) + (i shl 4)
                        i_slen2[n + 180] = i or (j shl 3) or (k shl 6) or (4 shl 12)
                        k++
                    }
                    j++
                }
                i++
            }
            i = 0
            while (i < 4) {
                j = 0
                while (j < 3) {
                    n = j + i * 3
                    i_slen2[n + 244] = i or (j shl 3) or (5 shl 12)
                    n_slen2[n + 500] = i or (j shl 3) or (2 shl 12) or (1 shl 15)
                    j++
                }
                i++
            }
            i = 0
            while (i < 5) {
                j = 0
                while (j < 5) {
                    k = 0
                    while (k < 4) {
                        l = 0
                        while (l < 4) {
                            n = l + (k shl 2) + (j shl 4) + i * 80
                            n_slen2[n] = i or (j shl 3) or (k shl 6) or (l shl 9)
                            l++
                        }
                        k++
                    }
                    j++
                }
                i++
            }
            i = 0
            while (i < 5) {
                j = 0
                while (j < 5) {
                    k = 0
                    while (k < 4) {
                        n = k + (j shl 2) + i * 20
                        n_slen2[n + 400] = i or (j shl 3) or (k shl 6) or (1 shl 12)
                        k++
                    }
                    j++
                }
                i++
            }
        }
    }

    class ChannelInformation() {
        // 从位流读取数据依次初始化的14个变量
        var part2_3_length = 0
        var big_values = 0
        var global_gain = 0
        var scalefac_compress = 0
        var window_switching_flag = 0
        var block_type = 0
        var mixed_block_flag = 0
        var table_select: IntArray
        val subblock_gain: IntArray
        var region0_count = 0
        var region1_count = 0
        var preflag = 0
        var scalefac_scale = 0
        var count1table_select = 0

        // 这3个通过计算初始化
        var region1Start = 0
        var region2Start = 0
        var part2_length // 增益因子(scale-factor)比特数
                = 0

        init {
            table_select = IntArray(3)
            subblock_gain = IntArray(3)
        }
    }
}
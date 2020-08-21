/*
* BitStream.java -- 读取位流
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

/**
 * 从输入的字节流获得位流。
 *
 * 对于Layer3文件解码， BitStream 仅用于读取帧边信息，要读取Layer3文件的主数据，必须使用[BitStreamMainData]。
 *
 * 对于Layer1和Layer2文件解码，用 BitStream 获得的位流，用于解码全部数据。
 */
open class BitStream(private val maxOff: Int, extra: Int) {
    @JvmField
	protected var bitPos = 0

    /**
     * 获取缓冲区字节指针。
     *
     * @return 缓冲区字节指针。
     */
    var bytePos = 0
        protected set
    @JvmField
	protected var bitReservoir: ByteArray

    /**
     * 获取缓冲区已经填入的字节数。
     *
     * @return 缓冲区已经填入的字节数。
     */
    var size //bitReservoir已填入的字节数
            = 0
        private set

    /**
     * 向缓冲区添加len字节。
     *
     * @param b
     * 源数据。
     * @param off
     * 源数据偏移量。
     * @param len
     * 源数据长度。
     * @return 实际填充到缓冲区的字节数。
     */
    fun append(b: ByteArray?, off: Int, len: Int): Int {
        var len = len
        if (len + size > maxOff) {
            // 将缓冲区bytePos及之后的(未处理过的)数据移动到缓冲区首
            System.arraycopy(bitReservoir, bytePos, bitReservoir, 0, size - bytePos)
            size -= bytePos
            bytePos = 0
            bitPos = bytePos
        }
        if (len + size > maxOff) len = maxOff - size
        System.arraycopy(b, off, bitReservoir, size, len)
        size += len
        return len
    }

    /**
     * 将缓冲指定为b，缓冲区初始偏移量由off指定。与 [.append]
     * 方法的区别是，本方法不会从源数据b复制数据。
     *
     * @param b
     * 源数据。
     * @param off
     * 源数据偏移量。
     */
    fun feed(b: ByteArray, off: Int) {
        bitReservoir = b
        bytePos = off
        bitPos = 0
    }

    /**
     * 从缓冲区读取一位。
     *
     * @return 0或1。
     */
    fun get1Bit(): Int {
        var bit: Int = bitReservoir[bytePos].toInt() shl bitPos
        bit = bit shr 7
        bit = bit and 0x1
        bitPos++
        bytePos += bitPos shr 3
        bitPos = bitPos and 0x7
        return bit
    }

    /**
     * 从缓冲区读取n位。由于运行时速度方面的原因，若读取的位数不大于9，请考虑用[.getBits9]方法更高效。
     *
     * @param n
     * 比特数，n=2..17时调用此方法。
     * @return n位的值。
     */
    fun getBits17(n: Int): Int {
        var iret = bitReservoir[bytePos].toInt()
        iret = iret shl 8
        iret = iret or (bitReservoir[bytePos + 1].toInt() and 0xff)
        iret = iret shl 8
        iret = iret or (bitReservoir[bytePos + 2].toInt() and 0xff)
        iret = iret shl bitPos
        iret = iret and 0xffffff // 高8位置0
        iret = iret shr 24 - n
        bitPos += n
        bytePos += bitPos shr 3
        bitPos = bitPos and 0x7
        return iret
    }

    /**
     * 从缓冲区读取n位。
     *
     * @param n
     * 比特数，n=2..9时调用此方法。
     * @return n位的值。
     */
    fun getBits9(n: Int): Int {
        var iret = bitReservoir[bytePos].toInt()
        iret = iret shl 8
        iret = iret or (bitReservoir[bytePos + 1].toInt() and 0xff)
        iret = iret shl bitPos
        iret = iret and 0xffff // 高16位置0
        iret = iret shr 16 - n
        bitPos += n
        bytePos += bitPos shr 3
        bitPos = bitPos and 0x7
        return iret
    }

    /**
     * 缓冲区丢弃n字节，缓冲区比特指针复位。
     *
     * @param n
     * 丢弃的字节数。
     */
    fun skipBytes(n: Int) {
        bytePos += n
        bitPos = 0
    }

    /**
     * 缓冲区丢弃或回退指定比特。
     *
     * @param n
     * 若n>0丢弃n比特，若n<0则回退-n比特。
     */
    fun skipBits(n: Int) {
        bitPos += n
        bytePos += bitPos shr 3
        bitPos = bitPos and 0x7
    }

    // ----debug----
    private var markPos = 0
    protected fun mark() {
        markPos = bytePos
    }

    protected val mark: Int
        protected get() = bytePos - markPos

    /**
     * 创建一个位流BitStream对象，位流的缓冲区大小len指定，位流的缓冲区尾部空出的长度由extra指定。
     *
     * @param len
     * 缓冲区可访问长度。<br></br>
     * 缓冲区用于解码帧边信息时len为9、17或32。<br></br>
     * 缓冲区用于解码主数据(main_data)时长度不小于最大帧长512+1732。
     *
     * @param extra
     * 缓冲区尾部空出的字节数，防止哈夫曼解码时位流有错误导致缓冲区溢出，尾部空出512字节(part2_3_length长度，
     * 2^12位)。
     */
    init {
        bitReservoir = ByteArray(maxOff + extra)
    }
}
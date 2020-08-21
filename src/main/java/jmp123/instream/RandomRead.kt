package jmp123.instream

import java.io.IOException

/**
 * 随机访问文件的读取。
 *
 * 源码下载： http://jmp123.sf.net/
 */
abstract class RandomRead {
    /**
     * 文件长度，单位：字节。
     */
    public var length: Long = 0
    /**
     * 获取音轨播放时长。
     *
     *
     * 在调用 [.open]
     * 打开多路复用媒体流过程中查找音频流时对多路复用媒体流解包时已经获取到该媒体的播放时长。本方法应在调用 [.open]
     * 之后被调用。
     *
     *
     * 如果调用 [.open] 时指定的文件名是MPEG音频流媒体文件，调用本方法总是返回零。应该在完成帧头解码之后调用
     * [#getDuration()][jmp123.decoder.Header] 获取音轨的播放时长。
     *
     * @return 音轨播放时长，单位：秒。
     */
    /**
     * 音轨播放时长，单位：秒。
     */
    var duration = 0
        protected set

    /**
     * 打开指定文件。name指定的文件可以是一个本地磁盘文件，也可以是一个网络文件。
     *
     * @param name
     * 文件名。
     * @param title
     * 歌曲标题，可以为**null**。
     * @return 成功打开返回true，否则返回false。
     * @throws FileNotFoundException
     * 如果指定文件不存在，或者它是一个目录，而不是一个常规文件，或因为其他某些原因而无法打开进行读取。
     * @throws MalformedURLException
     * 如果指定了未知协议。
     * @throws UnsupportedEncodingException
     * 如果指定的字符编码不被支持。
     * @throws SocketException
     * 如果底层协议出现错误，例如 TCP 错误。
     * @throws IllegalArgumentException
     * 如果端点为 null 或者此套接字不支持 SocketAddress 子类。
     * @throws SocketTimeoutException
     * 如果连接超时。
     * @throws IOException
     * 发生I/O错误。
     */
    @Throws(IOException::class)
    abstract fun open(name: String?, title: String?): Boolean

    /**
     * 关闭此输入流并释放与该流关联的所有系统资源。
     */
    abstract fun close()

    /**
     * 将流中最多 len 个数据字节读入 byte 数组。尝试读取 len 个字节，但读取的字节也可能小于该值。以整数形式返回实际读取的字节数。
     *
     * @param b
     * 读入数据的缓冲区。
     * @param off
     * 数组 b 中将写入数据的初始偏移量。
     * @param len
     * 要读取的最大字节数。
     * @return 读入缓冲区的总字节数；如果因为已到达流末尾而不再有数据可用，则返回0或-1。
     * @throws IOException
     * 如果不是因为流位于文件末尾而无法读取第一个字节；如果输入流已关闭；如果发生其他 I/O 错误。
     */
    @Throws(IOException::class)
    abstract fun read(b: ByteArray?, off: Int, len: Int): Int

    /**
     * 设置到此文件开头测量到的文件指针偏移量，在该位置发生下一个读取或写入操作。偏移量的设置可能会超出文件末尾。
     * 偏移量的设置超出文件末尾不会改变文件的长度。只有在偏移量的设置超出文件末尾的情况下对文件进行写入才会更改其长度。
     *
     * @param pos
     * 从文件开头以字节为单位测量的偏移量位置，在该位置设置文件指针。
     * @return 设置文件指针偏移量成功返回true，否则返回false。对于打开的网络文件，若服务器不支持随机读取，总是返回false。
     * @throws IOException
     * 如果 pos 小于 0 或者发生 I/O 错误。
     */
    @Throws(IOException::class)
    abstract fun seek(pos: Long): Boolean

    /**
     * 返回此文件的长度。
     *
     * @return 按字节测量的此文件的长度。
     * @throws IOException
     * 如果 pos 小于 0 或者发生 I/O 错误。
     */
    fun length(): Long {
        return length
    }
}
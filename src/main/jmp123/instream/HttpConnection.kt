package jmp123.instream

import java.io.IOException
import java.io.InputStream
import java.io.PrintWriter
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL
import java.util.*

/**
 * HttpConnection是java.net.HttpURLConnection的“缩水版”。
 * 由于网络原因I/O发生阻塞时调用 [.close] 能及时响应用户的中断。
 *
 */
class HttpConnection {
    private val socket: Socket

    /**
     * 获取从此打开的连接读取的输入流。
     *
     * @return 打开的连接读入的输入流。
     */
    var inputStream: InputStream? = null
        private set
    private val map: HashMap<String, String>
    private var response: String? = null
    private var StatusLine: String? = null

    /**
     * 获取响应码。
     *
     * @return 以整数形式返回响应码。
     */
    var responseCode = 0
        private set

    /**
     * 获取HTTP响应的简短描述信息。
     *
     * @return 响应的简短描述信息。
     */
    var responseMessage: String? = null
        private set

    /**
     * 获取 content-length 头字段的值。
     *
     * @return 返回 content-length 头字段的值。
     */
    var contentLength: Long = 0
        private set

    /**
     * 打开 指定的Socket连接并解析HTTP响应头。
     *
     * @param location
     * 目标URL。
     * @param referer
     * 引用网址。
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
    fun open(location: URL, referer: String?) {
        var referer = referer
        val host = location.host
        if (referer == null) referer = "http://$host/"
        val path = location.path
        var port = location.port
        if (port == -1) port = 80
        socket.soTimeout = 5000
        //socket.setReceiveBufferSize(32 * 1024);
        socket.connect(InetSocketAddress(host, port), 5000)
        val pw = PrintWriter(socket.getOutputStream(), true)

        // 构建HTTP请求头
        pw.println("GET $path HTTP/1.1")
        pw.println("Host: $host")
        pw.println("Referer: $referer")
        pw.println("Accept: */*")
        pw.println("User-Agent: Mozilla/4.0 (compatible; MSIE 6.0; Windows NT 5.1)")
        // pw.println("Range: bytes=" + startpos + "-");
        pw.println("Connection: Keep-Alive")
        pw.println()
        inputStream = socket.getInputStream()
        getResponse()
    }

    @Throws(IOException::class)
    private fun getResponse() {
        // 获取HTTP响应头
        val b = ByteArray(4096)
        var off: Int
        var `val`: Int
        var endcode = 0
        off = 0
        while (off < 4096 && endcode != 0x0d0a0d0a) {
            if (inputStream!!.read().also { `val` = it } == -1) break
            b[off] = `val`.toByte()
            endcode = endcode shl 8
            endcode = endcode or `val`
            off++
        }
        //System.out.println("off = " + off);
        if (endcode != 0x0d0a0d0a) throw IOException("HTTP response header not found.")

        // 解析响应头
        response = String(b, 0, off)
        val header = response!!.split("\r\n".toRegex()).toTypedArray()
        if (header.size < 1) throw IOException("Illegal response header.")
        StatusLine = header[0]
        parseStatusLine()
        var pair: Array<String>
        for (line in header) {
            pair = line.split(": ".toRegex()).toTypedArray()
            if (pair.size == 2) map[pair[0]] = pair[1]
        }
        try {
            contentLength = map["Content-Length"]!!.toLong()
        } catch (e: NumberFormatException) {
            contentLength = -1
        }
    }

    // StatusLine = HTTP-Version SPACE Response-Code SPACE Reason-Phrase
    @Throws(IOException::class)
    private fun parseStatusLine() {
        val s = StatusLine!!.split(" ".toRegex()).toTypedArray()
        if (s.size < 3) throw IOException("Illegal response status-line.")
        try {
            responseCode = s[1].toInt()
            responseMessage = s[2]
            for (i in 3 until s.size) responseMessage += " " + s[i]
        } catch (e: NumberFormatException) {
            responseCode = -1
            throw NumberFormatException("Illegal Response-Code: -1")
        }
    }

    /**
     * 返回指定的HTTP响应头字段的值。
     *
     * @param key
     * 头字段的名称。
     * @return 指定的头字段的值，或者如果头中没有这样一个字段，则返回 null。
     */
    fun getHeaderField(key: String): String? {
        return map[key]
    }

    /**
     * 在控制台打印HTTP响应头。
     */
    fun printResponse() {
        if (response != null) println(response)
    }

    /**
     * 关闭连接并清除（已经获取的）HTTP响应头。
     * @throws IOException 关闭Socket时发生I/O错误。
     */
    @Throws(IOException::class)
    fun close() {
        map.clear()
        socket.close()
    }

    /**
     * 构造一个连接。
     */
    init {
        socket = Socket()
        map = HashMap()
    }
}
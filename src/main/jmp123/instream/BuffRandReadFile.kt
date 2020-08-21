/*
* BuffRandAcceFile.java -- 本地文件随机读取
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
package jmp123.instream

import java.io.IOException
import java.io.RandomAccessFile

class BuffRandReadFile : RandomRead() {
    private var rafIn: RandomAccessFile? = null
    @Throws(IOException::class)
    override fun open(name: String?, title: String?): Boolean {
        rafIn = RandomAccessFile(name, "r")
        length = rafIn!!.length()
        return true
    }

    @Throws(IOException::class)
    override fun read(b: ByteArray?, off: Int, len: Int): Int {
        return rafIn!!.read(b, off, len)
    }

    @Throws(IOException::class)
    override fun seek(pos: Long): Boolean {
        rafIn!!.seek(pos)
        return true
    }

    override fun close() {
        try {
            rafIn!!.close()
        } catch (e: IOException) {
        }
    }
}
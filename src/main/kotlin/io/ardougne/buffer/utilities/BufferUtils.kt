package io.ardougne.buffer.utilities

import io.netty.buffer.ByteBuf

/**
 * @author Tom <rspsmods@gmail.com>
 * @author 0xTempest
 */
object BufferUtils
{

    fun ByteBuf.readString(): String
    {
        if (isReadable)
        {
            val start = readerIndex()
            while (readByte().toInt() != 0) {
                if (!isReadable) {
                    break
                }
            }
            val size = readerIndex() - start

            val data = ByteArray(size)
            readerIndex(start)
            readBytes(data)

            return String(data, 0, size - 1)
        }
        else
        {
            return ""
        }
    }

    fun ByteBuf.readJagexString(): String
        = if (isReadable && readByte().toInt() != 0) readString() else ""

}
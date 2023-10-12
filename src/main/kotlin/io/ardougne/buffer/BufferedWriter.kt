package io.ardougne.buffer

import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import io.ardougne.buffer.type.DataType
import io.ardougne.buffer.type.access.AccessMode
import io.ardougne.buffer.type.order.DataOrder
import io.ardougne.buffer.type.transform.DataTransformation
import io.ardougne.buffer.utilities.DataConstants

/**
 *
 * @author 0xTempest
 */
open class BufferedWriter(protected val buffer: ByteBuf = Unpooled.buffer(0))
{

    /**
     * The current bit index.
     */
    private var bitIndex: Int = 0

    /**
     * The current mode.
     */
    private var mode = AccessMode.BYTE_ACCESS

    private val byteBuf: ByteBuf = buffer

    val readableBytes: Int get() = buffer.readableBytes()

    /**
     * Gets the current length of the builder's buffer.
     *
     * @return The length of the buffer.
     */
    val length: Int
        get()
        {
            checkByteAccess()
            return buffer.writerIndex()
        }

    /**
     * Checks that this builder is in the bit access mode.
     *
     * @throws IllegalStateException If the builder is not in bit access mode.
     */
    protected fun checkBitAccess() = check(mode === AccessMode.BIT_ACCESS) { "For bit-based calls to work, the mode must be bit access." }

    /**
     * Checks that this builder is in the byte access mode.
     *
     * @throws IllegalStateException If the builder is not in byte access mode.
     */
    protected fun checkByteAccess() = check(mode === AccessMode.BYTE_ACCESS) { "For byte-based calls to work, the mode must be byte access." }

    /**
     * Puts a standard data type with the specified value, byte order and transformation.
     *
     * @param type The data type.
     * @param order The byte order.
     * @param transformation The transformation.
     * @param value The value.
     * @throws IllegalArgumentException If the type, order, or transformation is unknown.
     */
    fun put(
        type: DataType,
        order: DataOrder,
        transformation: DataTransformation,
        value: Number
    )
    {

        check(type != DataType.SMART) { "Use `putSmart` instead." }

        checkByteAccess()

        val longValue = value.toLong()

        val length = type.bytes

        when (order)
        {

            DataOrder.BIG -> for (i in length - 1 downTo 0)
            {
                if (i == 0 && transformation != DataTransformation.NONE)
                {
                    when (transformation)
                    {
                        DataTransformation.ADD      -> buffer.writeByte((longValue + 128).toByte().toInt())
                        DataTransformation.NEGATE   -> buffer.writeByte((-longValue).toByte().toInt())
                        DataTransformation.SUBTRACT -> buffer.writeByte((128 - longValue).toByte().toInt())
                        else                        -> throw IllegalArgumentException("Unknown transformation.")
                    }
                }
                else buffer.writeByte((longValue shr i * 8).toByte().toInt())
            }
            DataOrder.LITTLE -> for (i in 0..< length)
            {
                if (i == 0 && transformation != DataTransformation.NONE)
                {
                    when (transformation)
                    {
                        DataTransformation.ADD      -> buffer.writeByte((longValue + 128).toByte().toInt())
                        DataTransformation.NEGATE   -> buffer.writeByte((-longValue).toByte().toInt())
                        DataTransformation.SUBTRACT -> buffer.writeByte((128 - longValue).toByte().toInt())
                        else                        -> throw IllegalArgumentException("Unknown transformation.")
                    }
                }
                else
                {
                    buffer.writeByte((longValue shr i * 8).toByte()
                                             .toInt())
                }
            }
            DataOrder.MIDDLE          ->
            {
                check(transformation == DataTransformation.NONE) { "Middle endian cannot be transformed." }
                check(type == DataType.INT) { "Middle endian can only be used with an integer." }
                buffer.writeByte((longValue shr 8).toByte().toInt())
                buffer.writeByte(longValue.toByte().toInt())
                buffer.writeByte((longValue shr 24).toByte().toInt())
                buffer.writeByte((longValue shr 16).toByte().toInt())
            }
            DataOrder.INVERSE_MIDDLE ->
            {
                check(transformation == DataTransformation.NONE) { "Inversed middle endian cannot be transformed." }
                check(type == DataType.INT) { "Inversed middle endian can only be used with an integer." }
                buffer.writeByte((longValue shr 16).toByte().toInt())
                buffer.writeByte((longValue shr 24).toByte().toInt())
                buffer.writeByte(longValue.toByte().toInt())
                buffer.writeByte((longValue shr 8).toByte().toInt())
            }
        }
    }

    /**
     * Puts a standard data type with the specified value and byte order.
     *
     * @param type The data type.
     * @param order The byte order.
     * @param value The value.
     */
    fun put(
        type: DataType,
        order: DataOrder,
        value: Number
    ) = put(type, order, DataTransformation.NONE, value)

    /**
     * Puts a standard data type with the specified value and transformation.
     *
     * @param type The type.
     * @param transformation The transformation.
     * @param value The value.
     */
    fun put(
        type: DataType,
        transformation: DataTransformation,
        value: Number
    ) = put(type, DataOrder.BIG, transformation, value)

    /**
     * Puts a standard data type with the specified value.
     *
     * @param type The data type.
     * @param value The value.
     */
    fun put(
        type: DataType,
        value: Number
    ) = put(type, DataOrder.BIG, DataTransformation.NONE, value)

    /**
     * Puts a single bit into the buffer. If `flag` is `true`, the value of the bit is `1`. If
     * `flag` is `false`, the value of the bit is `0`.
     *
     * @param flag The flag.
     */
    fun putBit(flag: Boolean) = putBit(if (flag) 1 else 0)

    /**
     * Puts a single bit into the buffer with the value `value`.
     *
     * @param value The value.
     */
    fun putBit(value: Int) = putBits(1, value)

    fun putBits(numBits: Int, value: Boolean) = putBits(numBits, if(value) 1 else 0)

    /**
     * Puts `numBits` into the buffer with the value `value`.
     *
     * @param numBits The number of bits to put into the buffer.
     * @param value The value.
     * @throws IllegalArgumentException If the number of bits is not between 1 and 31 inclusive.
     */
    @Throws(IllegalArgumentException::class)
    fun putBits(
            numBits: Int,
            value: Int
    )
    {

        var numberOfBits = numBits

        check(numberOfBits in 1..32) { "Number of bits must be between 1 and 32 inclusive." }

        checkBitAccess()

        var bytePos = bitIndex shr 3
        var bitOffset = 8 - (bitIndex and 7)
        bitIndex += numberOfBits

        var requiredSpace = bytePos - buffer.writerIndex() + 1
        requiredSpace += (numberOfBits + 7) / 8
        buffer.ensureWritable(requiredSpace)

        while (numberOfBits > bitOffset)
        {
            var tmp = buffer.getByte(bytePos)
                    .toInt()
            tmp = tmp and DataConstants.BIT_MASK[bitOffset].inv()
            tmp = tmp or (value shr numberOfBits - bitOffset and DataConstants.BIT_MASK[bitOffset])
            buffer.setByte(bytePos++, tmp)
            numberOfBits -= bitOffset
            bitOffset = 8
        }
        var tmp = buffer.getByte(bytePos)
                .toInt()
        if (numberOfBits == bitOffset)
        {
            tmp = tmp and DataConstants.BIT_MASK[bitOffset].inv()
            tmp = tmp or (value and DataConstants.BIT_MASK[bitOffset])
            buffer.setByte(bytePos, tmp)
        }
        else
        {
            tmp = tmp and (DataConstants.BIT_MASK[numberOfBits] shl bitOffset - numberOfBits).inv()
            tmp = tmp or (value and DataConstants.BIT_MASK[numberOfBits] shl bitOffset - numberOfBits)
            buffer.setByte(bytePos, tmp)
        }
    }

    /**
     * Puts the specified byte array into the buffer.
     *
     * @param bytes The byte array.
     */
    fun putBytes(bytes: ByteArray) = buffer.writeBytes(bytes)

    /**
     * Puts the specified byte array into the buffer.
     *
     * @param bytes The byte array.
     */
    fun putBytes(
            bytes: ByteArray,
            position: Int,
            length: Int
    )
    {
        for (i in position until position + length)
            buffer.writeByte(bytes[i].toInt())
    }

    /**
     * Puts the specified byte array into the buffer.
     *
     * @param bytes The byte array.
     */
    fun putBytes(
        transformation: DataTransformation,
        bytes: ByteArray,
        position: Int,
        length: Int
    )
    {
        for (i in position until position + length)
            put(DataType.BYTE, transformation, bytes[i].toInt())
    }

    /**
     * Puts the bytes from the specified buffer into this packet's buffer.
     *
     * @param buffer The source [ByteBuf].
     */
    fun putBytes(buffer: ByteBuf)
    {
        val bytes = ByteArray(buffer.readableBytes())
        buffer.markReaderIndex()
        try
        {
            buffer.readBytes(bytes)
        }
        finally
        {
            buffer.resetReaderIndex()
        }
        putBytes(bytes)
    }

    /**
     * Puts the bytes into the buffer with the specified transformation.
     *
     * @param transformation The transformation.
     * @param bytes The byte array.
     */
    fun putBytes(
        transformation: DataTransformation,
        bytes: ByteArray
    )
    {
        if (transformation == DataTransformation.NONE)
            putBytes(bytes)
        else
            for (b in bytes)
                put(DataType.BYTE, transformation, b)
    }

    fun putBytes(
        transformation: DataTransformation,
        buffer: ByteBuf
    )
    {
        val bytes = ByteArray(buffer.readableBytes())
        buffer.markReaderIndex()
        try
        {
            buffer.readBytes(bytes)
        }
        finally
        {
            buffer.resetReaderIndex()
        }
        putBytes(transformation, bytes)
    }

    /**
     * Puts the specified byte array into the buffer in reverse.
     *
     * @param bytes The byte array.
     */
    fun putBytesReverse(bytes: ByteArray)
    {

        checkByteAccess()

        for (i in bytes.indices.reversed())
            buffer.writeByte(bytes[i].toInt())

    }

    /**
     * Puts the bytes from the specified buffer into this packet's buffer, in reverse.
     *
     * @param buffer The source [ByteBuf].
     */
    fun putBytesReverse(buffer: ByteBuf)
    {
        val bytes = ByteArray(buffer.readableBytes())
        buffer.markReaderIndex()
        try
        {
            buffer.readBytes(bytes)
        }
        finally
        {
            buffer.resetReaderIndex()
        }
        putBytesReverse(bytes)
    }

    /**
     * Puts the specified byte array into the buffer in reverse with the specified transformation.
     *
     * @param transformation The transformation.
     * @param bytes The byte array.
     */
    fun putBytesReverse(
        transformation: DataTransformation,
        bytes: ByteArray
    )
    {

        if (transformation == DataTransformation.NONE)
            putBytesReverse(bytes)
        else
            for (i in bytes.indices.reversed())
                put(DataType.BYTE, transformation, bytes[i])

    }

    /**
     * Puts a smart into the buffer.
     *
     * @param value The value.
     */
    fun putSmart(value: Int)
    {

        checkByteAccess()

        if (value >= 0x80)
            buffer.writeShort(value + 0x8000)
        else
            buffer.writeByte(value)

    }

    /**
     * Puts a string into the buffer.
     *
     * @param str The string.
     */
    fun putString(str: String)
    {

        checkByteAccess()

        val chars = str.toCharArray()

        for (c in chars)
            buffer.writeByte(c.toByte().toInt())

        buffer.writeByte(0)

    }

    /**
     * Switches this builder's mode to the bit access mode.
     *
     * @throws IllegalStateException If the builder is already in bit access mode.
     */
    fun switchToBitAccess()
    {

        check(mode !== AccessMode.BIT_ACCESS) { "Already in bit access mode." }

        mode = AccessMode.BIT_ACCESS

        bitIndex = buffer.writerIndex() * 8

    }

    /**
     * Switches this builder's mode to the byte access mode.
     *
     * @throws IllegalStateException If the builder is already in byte access mode.
     */
    fun switchToByteAccess()
    {

        check(mode !== AccessMode.BYTE_ACCESS) { "Already in bit access mode." }

        mode = AccessMode.BYTE_ACCESS

        buffer.writerIndex((bitIndex + 7) / 8)

    }

    fun toBufferedReader() : BufferedReader = BufferedReader(byteBuf.array().copyOfRange(0, byteBuf.readableBytes()))

    fun toRawBufferedReader() : BufferedReader = BufferedReader(byteBuf.array())

}
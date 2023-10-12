package io.ardougne.buffer

import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import io.ardougne.buffer.type.DataType
import io.ardougne.buffer.type.access.AccessMode
import io.ardougne.buffer.type.order.DataOrder
import io.ardougne.buffer.type.transform.DataTransformation
import io.ardougne.buffer.utilities.BufferUtils.readJagexString
import io.ardougne.buffer.utilities.BufferUtils.readString
import io.ardougne.buffer.utilities.DataConstants
import kotlin.math.pow

/**
 * A utility class for reading [ByteBuf]s.
 *
 * @author Graham
 * @author 0xTempest
 */
open class BufferedReader
{

    /**
     * The current bit index.
     */
    private var bitIndex: Int = 0

    /**
     * The current mode.
     */
    private var mode = AccessMode.BYTE_ACCESS

    /**
     * The ByteBuf.
     */
    private val buffer: ByteBuf

    /**
     * Gets a bit from the buffer.
     *
     * @return The value.
     * @throws IllegalStateException If the reader is not in bit access mode.
     */
    val bit: Int
        get() = getBits(1)

    /**
     * Gets the length of this reader.
     *
     * @return The length of this reader.
     */
    val length: Int
        get()
        {

            checkByteAccess()

            return buffer.writableBytes()

        }

    val readableBytes: Int
        get()
        {

            checkByteAccess()

            return buffer.readableBytes()

        }

    /**
     * Gets a signed smart from the buffer.
     *
     * @return The smart.
     * @throws IllegalStateException If this reader is not in byte access mode.
     */
    val signedSmart: Int
        get()
        {

            checkByteAccess()

            val peek = buffer.getByte(buffer.readerIndex()).toInt()

            return if (peek < 128)
                buffer.readByte() - 64
            else buffer.readShort() - 0xc000

        }

    /**
     * Gets a string from the buffer.
     *
     * @return The string.
     * @throws IllegalStateException If this reader is not in byte access mode.
     */
    val string: String
        get()
        {
            checkByteAccess()
            return buffer.readString()
        }

    val jagString: String
        get()
        {
            checkByteAccess()
            return buffer.readJagexString()
        }

    /**
     * Gets an unsigned smart from the buffer.
     *
     * @return The smart.
     * @throws IllegalStateException If this reader is not in byte access mode.
     */
    val unsignedSmart: Int
        get()
        {
            checkByteAccess()
            val peek = buffer.getByte(buffer.readerIndex()).toInt()
            return if (peek < 128) buffer.readByte().toInt()
            else buffer.readShort() - 0x8000
        }

    val bigSmart: Int
        get()
        {
            checkByteAccess()
            val peek = buffer.getByte(buffer.readerIndex()).toInt()
            return if(peek >= 0) buffer.readUnsignedShort() and 0xFFFF
            else buffer.readInt() and Integer.MAX_VALUE
        }

    val unsignedIntSmartShortCompat: Int
        get()
        {
            var var1 = 0
            var var2: Int
            var2 = unsignedSmart
            while (var2 == 32767)
            {
                var1 += 32767
                var2 = unsignedSmart
            }
            var1 += var2
            return var1
        }

    constructor() : this(Unpooled.buffer())

    constructor(bytes: ByteArray) : this(Unpooled.wrappedBuffer(bytes))

    constructor(buffer: ByteBuf)
    {
        this.buffer = buffer
    }

    /**
     * Checks that this reader is in the bit access mode.
     *
     * @throws IllegalStateException If the reader is not in bit access mode.
     */
    protected fun checkBitAccess() = check(mode === AccessMode.BIT_ACCESS) { "For bit-based calls to work, the mode must be bit access." }


    /**
     * Checks that this reader is in the byte access mode.
     *
     * @throws IllegalStateException If the reader is not in byte access mode.
     */
    protected fun checkByteAccess() = check(mode === AccessMode.BYTE_ACCESS) { "For byte-based calls to work, the mode must be byte access." }

    /**
     * Reads a standard data type from the buffer with the specified order and transformation.
     *
     * @param type The data type.
     * @param order The data order.
     * @param transformation The data transformation.
     * @return The value.
     * @throws IllegalStateException If this reader is not in byte access mode.
     * @throws IllegalArgumentException If the combination is invalid.
     */
    private operator fun get(
        type: DataType,
        order: DataOrder,
        transformation: DataTransformation
    ): Long
    {

        checkByteAccess()

        var longValue: Long = 0

        val length = type.bytes

        when (order)
        {

            DataOrder.BIG             ->
            {
                for (i in length - 1 downTo 0)
                {
                    longValue = if (i == 0 && transformation != DataTransformation.NONE)
                    {
                        when (transformation) {
                            DataTransformation.ADD      -> longValue or ((buffer.readByte().toLong() - 128 and 0xFFL))
                            DataTransformation.NEGATE   -> longValue or (-buffer.readByte().toLong() and 0xFFL)
                            DataTransformation.SUBTRACT -> longValue or ((128 - buffer.readByte().toLong() and 0xFFL))
                            else                        -> throw IllegalArgumentException("Unknown transformation.")
                        }
                    }
                    else
                        longValue or (buffer.readByte().toLong() and 0xFFL shl i * 8)
                }
            }

            DataOrder.LITTLE          ->
            {
                for (i in 0 until length)
                {
                    longValue = if (i == 0 && transformation != DataTransformation.NONE)
                        when (transformation)
                        {
                            DataTransformation.ADD      -> longValue or (buffer.readByte().toLong() - 128 and 0xFF)
                            DataTransformation.NEGATE   -> longValue or (-buffer.readByte().toLong() and 0xFF)
                            DataTransformation.SUBTRACT -> longValue or (128 - buffer.readByte().toLong() and 0xFF)
                            else                        -> throw IllegalArgumentException("Unknown transformation.")
                        }
                    else
                        longValue or (buffer.readByte().toLong() and 0xFFL shl i * 8)
                }
            }

            DataOrder.MIDDLE          ->
            {
                if (transformation != DataTransformation.NONE)
                    throw IllegalArgumentException("Middle endian cannot be transformed.")
                if (type != DataType.INT)
                    throw IllegalArgumentException("Middle endian can only be used with an integer.")
                longValue = longValue or (buffer.readByte().toInt() and 0xFF shl 8).toLong()
                longValue = longValue or (buffer.readByte().toInt() and 0xFF).toLong()
                longValue = longValue or (buffer.readByte().toInt() and 0xFF shl 24).toLong()
                longValue = longValue or (buffer.readByte().toInt() and 0xFF shl 16).toLong()
            }

            DataOrder.INVERSE_MIDDLE ->
            {
                if (transformation != DataTransformation.NONE)
                    throw IllegalArgumentException("Inversed middle endian cannot be transformed.")
                if (type != DataType.INT)
                    throw IllegalArgumentException("Inversed middle endian can only be used with an integer.")
                longValue = longValue or (buffer.readByte().toInt() and 0xFF shl 16).toLong()
                longValue = longValue or (buffer.readByte().toInt() and 0xFF shl 24).toLong()
                longValue = longValue or (buffer.readByte().toInt() and 0xFF).toLong()
                longValue = longValue or (buffer.readByte().toInt() and 0xFF shl 8).toLong()
            }

        }
        return longValue
    }

    /**
     * Gets the specified amount of bits from the buffer.
     *
     * @param amount The amount of bits.
     * @return The value.
     * @throws IllegalStateException If the reader is not in bit access mode.
     * @throws IllegalArgumentException If the number of bits is not between 1 and 31 inclusive.
     */
    fun getBits(amount: Int): Int
    {

        var amountOfBits = amount

        check(amountOfBits in 0..32) { "Number of bits must be between 1 and 32 inclusive." }

        checkBitAccess()

        var bytePos = bitIndex shr 3

        var bitOffset = 8 - (bitIndex and 7)

        var value = 0

        bitIndex += amountOfBits

        while (amountOfBits > bitOffset)
        {
            value += buffer.getByte(bytePos++).toInt() and DataConstants.BIT_MASK[bitOffset] shl amountOfBits - bitOffset
            amountOfBits -= bitOffset
            bitOffset = 8
        }
        value += if (amountOfBits == bitOffset)
            buffer.getByte(bytePos).toInt() and DataConstants.BIT_MASK[bitOffset]
        else
            buffer.getByte(bytePos).toInt() shr bitOffset - amountOfBits and DataConstants.BIT_MASK[amountOfBits]
        return value
    }

    /**
     * Gets bytes.
     *
     * @param bytes The target byte array.
     * @throws IllegalStateException If this reader is not in byte access mode.
     */
    fun getBytes(bytes: ByteArray)
    {

        checkByteAccess()

        for (i in bytes.indices)
            bytes[i] = buffer.readByte()

    }

    /**
     * Gets bytes with the specified transformation.
     *
     * @param transformation The transformation.
     * @param bytes The target byte array.
     * @throws IllegalStateException If this reader is not in byte access mode.
     */
    fun getBytes(
        transformation: DataTransformation,
        bytes: ByteArray
    )
    {

        if (transformation == DataTransformation.NONE)
            getBytesReverse(bytes)
        else
            for (i in bytes.indices)
                bytes[i] = getSigned(DataType.BYTE, transformation).toByte()

    }

    /**
     * Gets bytes in reverse.
     *
     * @param bytes The target byte array.
     * @throws IllegalStateException If this reader is not in byte access mode.
     */
    fun getBytesReverse(bytes: ByteArray)
    {

        checkByteAccess()

        for (i in bytes.indices.reversed())
            bytes[i] = buffer.readByte()

    }

    /**
     * Gets bytes in reverse with the specified transformation.
     *
     * @param transformation The transformation.
     * @param bytes The target byte array.
     * @throws IllegalStateException If this reader is not in byte access mode.
     */
    fun getBytesReverse(
        transformation: DataTransformation,
        bytes: ByteArray
    )
    {

        if (transformation == DataTransformation.NONE)
            getBytesReverse(bytes)
        else
            for (i in bytes.indices.reversed())
                bytes[i] = getSigned(DataType.BYTE, transformation).toByte()

    }

    /**
     * Gets a signed data type from the buffer with the specified order and transformation.
     *
     * @param type The data type.
     * @param order The byte order.
     * @param transformation The data transformation.
     * @return The value.
     * @throws IllegalStateException If this reader is not in byte access mode.
     * @throws IllegalArgumentException If the combination is invalid.
     */
    @JvmOverloads
    fun getSigned(
        type: DataType,
        order: DataOrder = DataOrder.BIG,
        transformation: DataTransformation = DataTransformation.NONE
    ): Long
    {

        var longValue = get(type, order, transformation)

        if (type != DataType.LONG)
        {

            val max = (2.0.pow((type.bytes * 8 - 1).toDouble()) - 1).toInt()

            if (longValue > max)
                longValue -= ((max + 1) * 2).toLong()

        }
        return longValue
    }

    /**
     * Gets a signed data type from the buffer with the specified transformation.
     *
     * @param type The data type.
     * @param transformation The data transformation.
     * @return The value.
     * @throws IllegalStateException If this reader is not in byte access mode.
     * @throws IllegalArgumentException If the combination is invalid.
     */
    fun getSigned(
        type: DataType,
        transformation: DataTransformation
    ): Long = getSigned(type, DataOrder.BIG, transformation)

    /**
     * Gets an unsigned data type from the buffer with the specified order and transformation.
     *
     * @param type The data type.
     * @param order The byte order.
     * @param transformation The data transformation.
     * @return The value.
     * @throws IllegalStateException If this reader is not in byte access mode.
     * @throws IllegalArgumentException If the combination is invalid.
     */
    @JvmOverloads
    fun getUnsigned(
        type: DataType,
        order: DataOrder = DataOrder.BIG,
        transformation: DataTransformation = DataTransformation.NONE
    ): Long
    {

        val longValue = get(type, order, transformation)

        check(type != DataType.LONG) { "Longs must be read as a signed type." }

        return longValue and -0x1L

    }

    /**
     * Gets an unsigned data type from the buffer with the specified transformation.
     *
     * @param type The data type.
     * @param transformation The data transformation.
     * @return The value.
     * @throws IllegalStateException If this reader is not in byte access mode.
     * @throws IllegalArgumentException If the combination is invalid.
     */
    fun getUnsigned(
        type: DataType,
        transformation: DataTransformation
    ): Long = getUnsigned(type, DataOrder.BIG, transformation)

    /**
     * Switches this builder's mode to the bit access mode.
     *
     * @throws IllegalStateException If the builder is already in bit access mode.
     */
    fun switchToBitAccess()
    {

        check(mode !== AccessMode.BIT_ACCESS) { "Already in bit access mode." }

        mode = AccessMode.BIT_ACCESS

        bitIndex = buffer.readerIndex() * 8

    }

    /**
     * Switches this builder's mode to the byte access mode.
     *
     * @throws IllegalStateException If the builder is already in byte access mode.
     */
    fun switchToByteAccess()
    {

        check(mode !== AccessMode.BYTE_ACCESS) { "Already in byte access mode." }

        mode = AccessMode.BYTE_ACCESS

        buffer.readerIndex((bitIndex + 7) / 8)

    }

    fun readBytes(byte: ByteArray) = buffer.readBytes(byte)

    fun readBytes(bytes: ByteArray, offset: Int, length: Int) = buffer.readBytes(bytes, offset, length)

    fun getBytes(index: Int, bytes: ByteArray) = buffer.getBytes(index, bytes)

    fun markReaderIndex(position: Int) = buffer.readerIndex(position)

    fun markReaderIndex() = buffer.markReaderIndex()

    fun readerIndex() = buffer.readerIndex()

    fun resetReaderIndex() = buffer.resetReaderIndex()

    fun byteArray() = buffer.array()

}
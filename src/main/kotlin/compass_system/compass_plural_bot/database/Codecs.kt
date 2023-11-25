package compass_system.compass_plural_bot.database

import kotlinx.datetime.LocalDate
import org.bson.BsonReader
import org.bson.BsonWriter
import org.bson.codecs.Codec
import org.bson.codecs.DecoderContext
import org.bson.codecs.EncoderContext

object UIntCodec : Codec<UInt> {
	override fun getEncoderClass() = UInt::class.java

	override fun encode(writer: BsonWriter, value: UInt, encoderContext: EncoderContext) {
		writer.writeInt32(value.toInt())
	}

	override fun decode(reader: BsonReader, decoderContext: DecoderContext): UInt {
		return reader.readInt32().toUInt()
	}
}

object LocalDateCodec : Codec<LocalDate> {
	override fun getEncoderClass() = LocalDate::class.java

	override fun encode(writer: BsonWriter, value: LocalDate, encoderContext: EncoderContext) {
		writer.writeString(value.toString())
	}

	override fun decode(reader: BsonReader, decoderContext: DecoderContext): LocalDate {
		return LocalDate.parse(reader.readString())
	}
}

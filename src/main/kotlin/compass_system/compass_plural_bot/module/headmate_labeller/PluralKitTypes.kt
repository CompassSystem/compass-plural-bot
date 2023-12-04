package compass_system.compass_plural_bot.module.headmate_labeller

import kotlinx.datetime.*
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.lang.IllegalStateException

@Serializable
data class PkSystem(
	val id: String,
	val name: String,
	val tag: String?,
	val members: List<PkMember> = emptyList(),
	@SerialName("avatar_url")
	val avatarUrl: String?
)

@Serializable
data class PkMember(
	val id: String,
	val name: String,
	@SerialName("display_name")
	val displayName: String?,
	val color: String?,
	@Serializable(with = BirthdaySerializer::class)
	val birthday: Instant?,
	val pronouns: String?,
	@SerialName("avatar_url")
	val avatarUrl: String?,
	val created: Instant,
	@SerialName("proxy_tags")
	val proxyTags: List<PkProxy>
)

class BirthdaySerializer : KSerializer<Instant> {
	override val descriptor = PrimitiveSerialDescriptor("compass_system.headmate_labeller.BirthdaySerializer", PrimitiveKind.STRING)

	override fun deserialize(decoder: Decoder): Instant {
		val value = decoder.decodeString()

		if (value.length == 4 + 3 + 3) {
			val year = value.take(4).toInt()
			val month = value.drop(5).take(2).toInt()
			val day = value.takeLast(2).toInt()

			return LocalDate(year, month, day).atTime(0, 0, 0, 0).toInstant(TimeZone.UTC)
		}

		throw SerializationException("Invalid birthday format.")
	}

	override fun serialize(encoder: Encoder, value: Instant) {
		TODO("Serialisation not yet needed.")
	}
}

@Serializable
data class PkProxy(
	val prefix: String? = null,
	val suffix: String? = null
) {
	override fun toString(): String {
		if (prefix == null && suffix == null) {
			throw IllegalStateException("Proxy must at least a prefix or a suffix.")
		}

		return "${prefix ?: ""}text${suffix ?: ""}"
	}
}

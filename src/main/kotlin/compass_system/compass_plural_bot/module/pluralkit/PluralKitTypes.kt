package compass_system.compass_plural_bot.module.pluralkit

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
	val members: MutableList<PkMember> = mutableListOf(),
	@SerialName("avatar_url")
	val avatarUrl: String?
)

@Serializable
data class Privacy(
	val visibility: String,
	@SerialName("name_privacy")
	val namePrivacy: String,
	@SerialName("description_privacy")
	val descriptionPrivacy: String,
	@SerialName("birthday_privacy")
	val birthdayPrivacy: String,
	@SerialName("pronoun_privacy")
	val pronounPrivacy: String,
	@SerialName("avatar_privacy")
	val avatarPrivacy: String,
	@SerialName("metadata_privacy")
	val metadataPrivacy: String,
	@SerialName("proxy_privacy")
	val proxyPrivacy: String
)

@Serializable
data class PartialMember(
	val name: String,
	@SerialName("display_name")
	val displayName: String? = null,
	val color: String? = null,
	@Serializable(with = BirthdaySerializer::class)
	val birthday: Instant? = null,
	val pronouns: String? = null,
	@SerialName("avatar_url")
	val avatarUrl: String? = null,
	@SerialName("webhook_avatar_url")
	val webhookAvatarUrl: String? = null,
	val banner: String? = null,
	val description: String? = null,
	@SerialName("keep_proxy")
	val keepProxy: Boolean,
	val tts: Boolean,
	@SerialName("autoproxy_enabled")
	val autoProxyEnabled: Boolean? = null,
	@SerialName("proxy_tags")
	val proxyTags: List<PkProxy>,
	val privacy: Privacy? = null
)

@Serializable
data class PkMember(
	val id: String,
	val name: String,
	@SerialName("display_name")
	val displayName: String? = null,
	val color: String? = null,
	@Serializable(with = BirthdaySerializer::class)
	val birthday: Instant? = null,
	val pronouns: String? = null,
	@SerialName("avatar_url")
	val avatarUrl: String? = null,
	@SerialName("webhook_avatar_url")
	val webhookAvatarUrl: String? = null,
	val banner: String? = null,
	val description: String? = null,
	val created: Instant? = null,
	@SerialName("keep_proxy")
	val keepProxy: Boolean,
	val tts: Boolean,
	@SerialName("autoproxy_enabled")
	val autoProxyEnabled: Boolean? = null,
	@SerialName("message_count")
	val messageCount: Int? = null,
	@SerialName("last_message_timestamp")
	val lastMessageTimestamp: Instant? = null,
	@SerialName("proxy_tags")
	val proxyTags: List<PkProxy>,
	val privacy: Privacy? = null
) {
	fun asPartial() = PartialMember(
		name = name,
		displayName = displayName,
		color = color,
		birthday = birthday,
		pronouns = pronouns,
		avatarUrl = avatarUrl,
		webhookAvatarUrl = webhookAvatarUrl,
		banner = banner,
		description = description,
		keepProxy = keepProxy,
		tts = tts,
		autoProxyEnabled = autoProxyEnabled,
		proxyTags = proxyTags,
		privacy = privacy
	)
}

class BirthdaySerializer : KSerializer<Instant> {
	override val descriptor = PrimitiveSerialDescriptor("compass_system.compass_plural_bot.module.pluralkit.BirthdaySerializer", PrimitiveKind.STRING)

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
		val date = value.toLocalDateTime(TimeZone.UTC).date
		encoder.encodeString("${date.year}-${date.monthNumber.toString().padStart(2, '0')}-${date.dayOfMonth.toString().padStart(2, '0')}")
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

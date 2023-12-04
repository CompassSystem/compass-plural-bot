package compass_system.compass_plural_bot.database

import kotlinx.serialization.Serializable

@Serializable
data class Metadata(
	val id: String,
	val version: UInt
)

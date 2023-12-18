package compass_system.compass_plural_bot.module.pluralkit

import com.kotlindiscord.kord.extensions.builders.ExtensibleBotBuilder
import com.kotlindiscord.kord.extensions.utils.env
import com.mongodb.kotlin.client.coroutine.MongoDatabase
import compass_system.compass_plural_bot.module.BotModule
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json

object PluralKitModule : BotModule {
	@OptIn(ExperimentalSerializationApi::class)
	val json: Json = Json {
		prettyPrint = true
		prettyPrintIndent = "  "
		encodeDefaults = true
		ignoreUnknownKeys = true
	}
	private val PLURALKIT_TOKEN = env("PLURALKIT_TOKEN")

	override fun getId(): String = "compass_system:pluralkit"

	override suspend fun applyDatabaseMigrations(database: MongoDatabase, version: UInt): UInt {
		return 0u
	}

	override suspend fun applyToBot(bot: ExtensibleBotBuilder) {
		bot.extensions {
			add { PluralKitExtension(PLURALKIT_TOKEN) }
		}
	}
}

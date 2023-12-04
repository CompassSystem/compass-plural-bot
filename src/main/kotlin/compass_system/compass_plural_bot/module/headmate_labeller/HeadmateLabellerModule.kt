package compass_system.compass_plural_bot.module.headmate_labeller

import com.kotlindiscord.kord.extensions.builders.ExtensibleBotBuilder
import com.mongodb.kotlin.client.coroutine.MongoDatabase
import compass_system.compass_plural_bot.module.BotModule

class HeadmateLabellerModule(private val pluralKitToken: String) : BotModule {
	override fun getId(): String = "compass_system:headmate_labeller"

	override suspend fun applyDatabaseMigrations(database: MongoDatabase, version: UInt): UInt {
		return 0u
	}

	override suspend fun applyToBot(bot: ExtensibleBotBuilder) {
		bot.extensions {
			add { HeadmateLabellerExtension(pluralKitToken) }
		}
	}
}

package compass_system.compass_plural_bot.module.headmate_store

import com.kotlindiscord.kord.extensions.builders.ExtensibleBotBuilder
import com.mongodb.kotlin.client.coroutine.MongoDatabase
import compass_system.compass_plural_bot.module.BotModule

object HeadmateStoreModule : BotModule {
	override fun getId(): String = "compass_system:headmate_store"

	override suspend fun applyDatabaseMigrations(database: MongoDatabase, version: UInt): UInt {
		return 0u
	}

	override suspend fun applyToBot(bot: ExtensibleBotBuilder) {
		bot.extensions {
			add(::HeadmateStoreExtension)
		}
	}
}

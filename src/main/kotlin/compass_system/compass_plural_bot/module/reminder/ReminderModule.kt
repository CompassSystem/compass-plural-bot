package compass_system.compass_plural_bot.module.reminder

import com.kotlindiscord.kord.extensions.builders.ExtensibleBotBuilder
import com.mongodb.kotlin.client.coroutine.MongoDatabase
import compass_system.compass_plural_bot.module.BotModule

object ReminderModule : BotModule {
	override fun getId(): String = "compass_system:reminder"

	override suspend fun applyDatabaseMigrations(database: MongoDatabase, version: UInt): UInt {
		if (version < 1u) {
			database.createCollection("reminders")

		}
		return 1u
	}

	override suspend fun applyToBot(bot: ExtensibleBotBuilder) {
		bot.extensions {
			add(::ReminderExtension)
		}
	}
}

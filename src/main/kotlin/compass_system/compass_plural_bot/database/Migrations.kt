package compass_system.compass_plural_bot.database

import com.mongodb.kotlin.client.coroutine.MongoDatabase
import kotlin.reflect.full.callSuspend
import kotlin.reflect.full.declaredFunctions

object Migrations {
	suspend fun v1(database: MongoDatabase) {
		database.createCollection("tasks")
	}

	suspend fun runAfter(currentVersion: UInt, database: MongoDatabase, onMigrated: suspend (version: UInt) -> Unit) {
		// todo: use KSP instead for creating the Map<UInt, (MongoDatabase) -> Unit>
		val migrations = Migrations::class.declaredFunctions
			.filter { it.name.startsWith("v") }
			.associateBy { it.name.substring(1).toUInt() }
			.toSortedMap()
			.tailMap(currentVersion + 1u)

		if (migrations.isNotEmpty()) {
			migrations.values.forEach { it.callSuspend(Migrations, database) }

			onMigrated.invoke(migrations.lastKey())
		}
	}
}
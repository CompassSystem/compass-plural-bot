package compass_system.compass_plural_bot.database

import com.mongodb.client.model.Filters
import com.mongodb.client.model.FindOneAndReplaceOptions
import com.mongodb.kotlin.client.coroutine.MongoDatabase
import kotlinx.coroutines.flow.firstOrNull

suspend fun <T : Any> MongoDatabase.getSingleton(collectionName: String, type: Class<T>): T? {
	return getCollection(collectionName, type).find(Filters.eq("single")).firstOrNull()
}

suspend fun <T : Any> MongoDatabase.upsertSingleton(collectionName: String, type: Class<T>, value: T) {
	getCollection(collectionName, type).findOneAndReplace(
		Filters.eq("single"),
		value,
		FindOneAndReplaceOptions().upsert(true)
	)
}

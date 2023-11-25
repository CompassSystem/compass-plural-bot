package compass_system.compass_plural_bot.extensions

import com.kotlindiscord.kord.extensions.DISCORD_BLURPLE
import com.kotlindiscord.kord.extensions.checks.hasPermission
import com.kotlindiscord.kord.extensions.commands.Arguments
import com.kotlindiscord.kord.extensions.commands.application.slash.EphemeralSlashCommandContext
import com.kotlindiscord.kord.extensions.commands.application.slash.converters.impl.stringChoice
import com.kotlindiscord.kord.extensions.commands.application.slash.ephemeralSubCommand
import com.kotlindiscord.kord.extensions.commands.converters.impl.int
import com.kotlindiscord.kord.extensions.components.components
import com.kotlindiscord.kord.extensions.components.forms.ModalForm
import com.kotlindiscord.kord.extensions.events.EventContext
import com.kotlindiscord.kord.extensions.extensions.Extension
import com.kotlindiscord.kord.extensions.extensions.ephemeralSlashCommand
import com.kotlindiscord.kord.extensions.extensions.event
import com.mongodb.kotlin.client.coroutine.MongoDatabase
import compass_system.compass_plural_bot.database.getSingleton
import compass_system.compass_plural_bot.database.upsertSingleton
import dev.kord.common.entity.ButtonStyle
import dev.kord.common.entity.DiscordPartialEmoji
import dev.kord.common.entity.Permission
import dev.kord.common.entity.Snowflake
import dev.kord.core.behavior.channel.createEmbed
import dev.kord.core.behavior.channel.createMessage
import dev.kord.core.behavior.edit
import dev.kord.core.behavior.interaction.respondEphemeral
import dev.kord.core.entity.channel.MessageChannel
import dev.kord.core.event.interaction.ButtonInteractionCreateEvent
import dev.kord.rest.builder.message.EmbedBuilder
import dev.kord.rest.builder.message.actionRow
import dev.kord.rest.builder.message.embed
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.*
import kotlinx.datetime.TimeZone
import kotlinx.serialization.Serializable
import org.koin.core.component.inject
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit
import kotlin.math.ceil
import kotlin.time.Duration.Companion.days

@Serializable
data class TaskExtensionSettings(
	val lastUpdateDate: LocalDate? = null,
	val estrogenEmbedMessage: Snowflake? = null,
	val estrogenEmbedChannel: Snowflake? = null,
	val estrogenDayCheck: String? = null,
	val estrogenDayCheckDate: LocalDate? = null,
	val courseraCoursesCompleted: UInt? = null,
	val totalCourseraCourses: UInt? = null,
	val courseraFinishDate: LocalDate? = null
)

class TaskExtension : Extension() {
	override val name = "TaskExtension"

	private val database: MongoDatabase by inject()
	private var settings: TaskExtensionSettings? = null

	private val timeZone = TimeZone.of("Europe/London")
	private val executor = ScheduledThreadPoolExecutor(1)

	override suspend fun setup() {
		ephemeralSlashCommand {
			name = "task"
			description = "Task extension commands"

			check { hasPermission(Permission.Administrator) }

			ephemeralSubCommand(::CreateEstrogenEmbedArgs) {
				name = "create-estrogen-embed"
				description = "Create the estrogen embed"

				action { createEstrogenEmbed() }
			}

			ephemeralSubCommand(::CreateCourseraEmbedArgs) {
				name = "create-coursera-embed"
				description = "Create the coursera embed"

				action { createCourseraEmbed() }
			}
		}

		event<ButtonInteractionCreateEvent> {
			check {
				failIfNot(event.interaction.componentId == "coursera_embed:complete_course")

				hasPermission(Permission.Administrator)
			}

			action { courseComplete() }
		}

		settings = database.getSingleton("tasks", TaskExtensionSettings::class.java)

		scheduleEstrogenEmbedUpdate()
	}

	//#region // Coursera Embed
	private suspend fun EphemeralSlashCommandContext<CreateCourseraEmbedArgs, ModalForm>.createCourseraEmbed() {
		val localSettings = settings ?: TaskExtensionSettings()

		settings = localSettings.copy(
			courseraCoursesCompleted = arguments.completed.toUInt(),
			totalCourseraCourses = arguments.completed.toUInt() + arguments.remaining.toUInt(),
			courseraFinishDate = arguments.endDate
		).also {
			database.upsertSingleton("tasks", TaskExtensionSettings::class.java, it)
		}

		event.interaction.getChannel().createMessage {
			embed { buildCourseraEmbed(settings!!) }

			actionRow {
				interactionButton(ButtonStyle.Primary, "coursera_embed:complete_course") {
					emoji = DiscordPartialEmoji(name = "⬆️")
				}
			}
		}

		respond { content = "Coursera embed created." }
	}

	private fun EmbedBuilder.buildCourseraEmbed(settings: TaskExtensionSettings) {
		if (
			settings.courseraCoursesCompleted == null ||
			settings.totalCourseraCourses == null ||
			settings.courseraFinishDate == null
		) {
			return
		}

		val remainingCourses = settings.totalCourseraCourses - settings.courseraCoursesCompleted
		val daysUntilEnd = Clock.System.now().toLocalDateTime(timeZone).date.daysUntil(settings.courseraFinishDate)

		val perWeek = ceil(daysUntilEnd / remainingCourses.toDouble()).toInt()

		val endDateAsString = settings.courseraFinishDate.let {
			"${it.dayOfMonth.toString().padStart(2, '0')}/${it.monthNumber.toString().padStart(2, '0')}/${it.year}"
		}

		title = "Coursera reminder"
		description = """
					|You have **${remainingCourses}** courses remaining to complete by **${endDateAsString}**.
					|
					|You should try to complete **${perWeek}** courses per week.
					|""".trimMargin().trimStart()
		color = DISCORD_BLURPLE
		timestamp = Clock.System.now()
	}

	private suspend fun EventContext<ButtonInteractionCreateEvent>.courseComplete() {
		val localSettings = settings?.run {
			copy(
				courseraCoursesCompleted = courseraCoursesCompleted?.plus(1u)
			)
		} ?: return

		settings = localSettings.also {
			database.upsertSingleton("tasks", TaskExtensionSettings::class.java, it)
		}

		if (localSettings.courseraCoursesCompleted == localSettings.totalCourseraCourses) {
			event.interaction.message.edit {
				embed {
					title = "Congratulations!"
					description = "You have completed all **${localSettings.courseraCoursesCompleted}** of your Coursera courses!"
					color = DISCORD_BLURPLE
					timestamp = Clock.System.now()
				}

				components { removeAll() }
			}
		} else {
			event.interaction.message.edit {
				embed { buildCourseraEmbed(localSettings) }
			}
		}

		event.interaction.respondEphemeral { content = "Course completed, good job!" }
	}

	//#endregion
	//#region // Estrogen Embed
	private suspend fun EphemeralSlashCommandContext<CreateEstrogenEmbedArgs, ModalForm>.createEstrogenEmbed() {
		val localSettings = settings ?: TaskExtensionSettings()

		val message = event.interaction.getChannel().createEmbed {
			buildEstrogenEmbed(arguments.leg)
		}

		settings = localSettings.copy(
			estrogenEmbedMessage = message.id,
			estrogenEmbedChannel = message.channelId,
			estrogenDayCheck = arguments.leg,
			estrogenDayCheckDate = Clock.System.now().toLocalDateTime(timeZone).date
		).also {
			database.upsertSingleton("tasks", TaskExtensionSettings::class.java, it)
		}

		respond { content = "Estrogen embed created." }
	}

	private fun updateEstrogenEmbed() {
		runBlocking {
			val settings = settings ?: return@runBlocking // Cannot update non-existent embed

			if ( // Ensure these properties are not null
				settings.estrogenEmbedMessage == null ||
				settings.estrogenEmbedChannel == null ||
				settings.estrogenDayCheck == null ||
				settings.estrogenDayCheckDate == null
			) {
				return@runBlocking
			}

			val today = Clock.System.now().toLocalDateTime(timeZone).date

			if (settings.estrogenDayCheckDate == today) {
				return@runBlocking
			}

			val leg = if (settings.estrogenDayCheckDate.daysUntil(today) % 2 == 0) {
				settings.estrogenDayCheck
			} else {
				if (settings.estrogenDayCheck == "left") {
					"right"
				} else {
					"left"
				}
			}

			val message = kord.getChannelOf<MessageChannel>(settings.estrogenEmbedChannel)!!.getMessage(settings.estrogenEmbedMessage)

			message.edit {
				embed { buildEstrogenEmbed(leg) }
			}
		}
	}

	private fun EmbedBuilder.buildEstrogenEmbed(leg: String) {
		title = "Estrogen reminder"
		description = """
						|Today is **${leg}** leg day.
						|
						|Remember to apply 2 doses of estrogen to your **${leg}** leg.
						|""".trimMargin().trimStart()
		color = DISCORD_BLURPLE
		timestamp = Clock.System.now()
	}

	private fun scheduleEstrogenEmbedUpdate() {
		val now = Clock.System.now().toLocalDateTime(timeZone)

		val start = if (now.hour > 7) {
			updateEstrogenEmbed()
			LocalDateTime(now.date.plus(1, DateTimeUnit.DAY), LocalTime(7, 0, 0, 0))
		} else {
			LocalDateTime(now.date, LocalTime(7, 0, 0, 0))
		}

		val delay = (start.toInstant(timeZone) - now.toInstant(timeZone)).inWholeSeconds

		executor.scheduleAtFixedRate(::updateEstrogenEmbed, delay, 1.days.inWholeSeconds, TimeUnit.SECONDS)
	}
	//#endregion
}

class CreateEstrogenEmbedArgs : Arguments() {
	val leg by stringChoice {
		name = "leg"
		description = "The leg to apply 2 doses of estrogen to today."

		choices = mutableMapOf(
			"Left leg" to "left",
			"Right leg" to "right"
		)
	}
}

class CreateCourseraEmbedArgs : Arguments() {
	val completed by int {
		name = "completed"
		description = "The number of courses completed."
	}

	val remaining by int {
		name = "remaining"
		description = "The remaining number of courses to complete."
	}

	val endDate by localDate {
		name = "end-date"
		description = "The deadline to finish courses by."
	}
}

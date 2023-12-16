package compass_system.compass_plural_bot.module.reminder

import com.kotlindiscord.kord.extensions.DISCORD_BLURPLE
import com.kotlindiscord.kord.extensions.checks.hasPermission
import com.kotlindiscord.kord.extensions.commands.Arguments
import com.kotlindiscord.kord.extensions.commands.application.slash.EphemeralSlashCommandContext
import com.kotlindiscord.kord.extensions.commands.application.slash.converters.impl.stringChoice
import com.kotlindiscord.kord.extensions.commands.application.slash.ephemeralSubCommand
import com.kotlindiscord.kord.extensions.commands.converters.impl.boolean
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
import dev.kord.common.entity.*
import dev.kord.core.behavior.channel.createEmbed
import dev.kord.core.behavior.channel.createMessage
import dev.kord.core.behavior.edit
import dev.kord.core.behavior.interaction.modal
import dev.kord.core.behavior.interaction.respondEphemeral
import dev.kord.core.entity.channel.MessageChannel
import dev.kord.core.event.interaction.ButtonInteractionCreateEvent
import dev.kord.core.event.interaction.ModalSubmitInteractionCreateEvent
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
	val courseraFinishDate: LocalDate? = null,
	val antiAndrogenCutCount: UInt? = null,
	val antiAndrogenCount: UInt? = null,
)

class ReminderExtension : Extension() {
	override val name = "ReminderExtension"

	private val database: MongoDatabase by inject()
	private var settings: TaskExtensionSettings? = null

	private val timeZone = TimeZone.of("Europe/London")
	private val executor = ScheduledThreadPoolExecutor(1)

	override suspend fun setup() {
		ephemeralSlashCommand {
			name = "reminders"
			description = "Reminder extension commands"

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

			ephemeralSubCommand(::CreateAntiAndrogenEmbedArgs) {
				name = "create-anti-androgen-embed"
				description = "Create the anti-androgen embed"

				action { createAntiAndrogenEmbed() }
			}

			ephemeralSubCommand(::CreateTestEmbedArgs) {
				name = "create-test-embed"
				description = "Create the test embed"

				action {
					event.interaction.getChannel().createMessage {
						embed {
							title = "Test embed"
							description = "This is a test embed. <:star_empty:1179024220861243452>"
							color = DISCORD_BLURPLE
							field {
								name = "Complete"
								value = "<:star_empty:1179024220861243452>"
							}
							timestamp = Clock.System.now()
						}
					}
				}
			}
		}

		val buttonEvents = buildMap<String, suspend EventContext<ButtonInteractionCreateEvent>.() -> Unit> {
			put("coursera_embed:complete_course") { courseComplete() }
			put("anti_androgen_embed:use") { useAntiAndrogen() }
			put("anti_androgen_embed:cut") { cutAntiAndrogen() }
			put("anti_androgen_embed:add") { addAntiAndrogensButton() }
		}

		event<ButtonInteractionCreateEvent> {
			check {
				failIfNot(event.interaction.componentId in buttonEvents)

				hasPermission(Permission.Administrator)
			}

			action { buttonEvents[event.interaction.componentId]?.invoke(this) }
		}

		event<ModalSubmitInteractionCreateEvent> {
			check {
				failIfNot(event.interaction.modalId == "anti_androgen_embed:add_modal")
			}

			action {
				addAntiAndrogensModal()
			}
		}

		settings = database.getSingleton("reminders", TaskExtensionSettings::class.java)

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
			database.upsertSingleton("reminders", TaskExtensionSettings::class.java, it)
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

		val perWeek = ceil(7 * remainingCourses.toDouble() / daysUntilEnd).toInt()

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
			database.upsertSingleton("reminders", TaskExtensionSettings::class.java, it)
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
			database.upsertSingleton("reminders", TaskExtensionSettings::class.java, it)
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
	//#region // Anti-Androgen Embed
	private suspend fun EphemeralSlashCommandContext<CreateAntiAndrogenEmbedArgs, ModalForm>.createAntiAndrogenEmbed() {
		val localSettings = settings ?: TaskExtensionSettings()

		settings = localSettings.copy(
			antiAndrogenCutCount = arguments.cutCount.toUInt(),
			antiAndrogenCount = arguments.uncutCount.toUInt()
		).also {
			database.upsertSingleton("reminders", TaskExtensionSettings::class.java, it)
		}

		event.interaction.getChannel().createMessage {
			embed { buildAntiAndrogenEmbed(settings!!) }
			actionRow {
				interactionButton(ButtonStyle.Primary, "anti_androgen_embed:use") {
					label = "Use"
					emoji = DiscordPartialEmoji(name = "💊")
				}
				interactionButton(ButtonStyle.Primary, "anti_androgen_embed:cut") {
					label = "Cut"
					emoji = DiscordPartialEmoji(name = "✂️")
				}
				interactionButton(ButtonStyle.Primary, "anti_androgen_embed:add") {
					label = "Add"
					emoji = DiscordPartialEmoji(name = "📦")
				}
			}
		}

		respond { content = "Anti-androgen embed created." }
	}

	private fun EmbedBuilder.buildAntiAndrogenEmbed(settings: TaskExtensionSettings) {
		if (
			settings.antiAndrogenCutCount == null ||
			settings.antiAndrogenCount == null
		) {
			return
		}

		val cutCount = settings.antiAndrogenCutCount
		val cutString = if (cutCount == 1u) {
			"**${cutCount}** day"
		} else {
			"**${cutCount}** days"
		}

		val uncutString = run {
			val uncutCount = settings.antiAndrogenCount * 4u
			val uncutWeeks = uncutCount / 7u
			val uncutDays = uncutCount - (uncutWeeks * 7u)

			val weekString = when (uncutWeeks) {
				0u -> ""
				1u -> "**${uncutWeeks}** week"
				else -> "**${uncutWeeks}** weeks"
			}
			val dayString = when (uncutDays) {
				0u -> ""
				1u -> "**${uncutDays}** day"
				else -> "**${uncutDays}** days"
			}

			if (weekString.isEmpty() && dayString.isEmpty()) {
				"**0** days"
			} else if (weekString.isEmpty()) {
				dayString
			} else if (dayString.isEmpty()) {
				weekString
			} else {
				"$weekString and $dayString"
			}
		}

		title = "Anti-androgen reminder"
		description = "You have $cutString prepared and $uncutString remaining."
		color = DISCORD_BLURPLE
		timestamp = Clock.System.now()
	}

	private suspend fun EventContext<ButtonInteractionCreateEvent>.useAntiAndrogen() {
		val localSettings = settings ?: return

		localSettings.antiAndrogenCutCount ?: return

		if (localSettings.antiAndrogenCutCount == 0u) {
			event.interaction.respondEphemeral { content = "You have no anti-androgens prepared." }

			return
		}

		settings = localSettings.copy(
			antiAndrogenCutCount = localSettings.antiAndrogenCutCount - 1u,
		).also {
			database.upsertSingleton("reminders", TaskExtensionSettings::class.java, it)
		}

		event.interaction.message.edit {
			embed { buildAntiAndrogenEmbed(settings!!) }
		}

		event.interaction.respondEphemeral { content = "Anti-androgen used." }
	}

	private suspend fun EventContext<ButtonInteractionCreateEvent>.cutAntiAndrogen() {
		val localSettings = settings ?: return

		if (
			localSettings.antiAndrogenCutCount == null ||
			localSettings.antiAndrogenCount == null
		) {
			return
		}

		if (localSettings.antiAndrogenCount == 0u) {
			event.interaction.respondEphemeral { content = "You have no anti-androgens remaining." }

			return
		}

		settings = localSettings.copy(
			antiAndrogenCutCount = localSettings.antiAndrogenCutCount + 4u,
			antiAndrogenCount = localSettings.antiAndrogenCount - 1u
		).also {
			database.upsertSingleton("reminders", TaskExtensionSettings::class.java, it)
		}

		event.interaction.message.edit {
			embed { buildAntiAndrogenEmbed(settings!!) }
		}

		event.interaction.respondEphemeral { content = "Anti-androgen cut." }
	}

	private suspend fun EventContext<ButtonInteractionCreateEvent>.addAntiAndrogensButton() {
		event.interaction.modal("Add anti-androgens", "anti_androgen_embed:add_modal") {
			actionRow {
				textInput(TextInputStyle.Short, "anti_androgen_embed:add_amount", "Amount") {
					required = true
					allowedLength = 0..3
				}
			}
		}
	}

	private suspend fun EventContext<ModalSubmitInteractionCreateEvent>.addAntiAndrogensModal() {
		val amount = event.interaction.textInputs["anti_androgen_embed:add_amount"]?.value?.toUInt() ?: return

		val localSettings = settings ?: return

		localSettings.antiAndrogenCount ?: return

		settings = localSettings.copy(
			antiAndrogenCount = localSettings.antiAndrogenCount + amount
		).also {
			database.upsertSingleton("reminders", TaskExtensionSettings::class.java, it)
		}

		event.interaction.message?.edit {
			embed { buildAntiAndrogenEmbed(settings!!) }
		}

		event.interaction.respondEphemeral { content = "$amount anti-androgens added." }
	}

//	private suspend fun EphemeralSlashCommandContext<NewAntiAndrogensArgs, ModalForm>.incrementAntiAndrogenCount() {
//		respond { content = "" }
//	}
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

class CreateAntiAndrogenEmbedArgs : Arguments() {
	val cutCount by int {
		name = "cut-count"
		description = "The number of uncut anti-androgens."
	}

	val uncutCount by int {
		name = "uncut-count"
		description = "The number of uncut anti-androgens."
	}
}

class CreateTestEmbedArgs : Arguments() {
	val complete by boolean {
		name = "complete"
		description = "Whether the task is complete."
	}
}

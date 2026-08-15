package ru.maxalert.notifier

/** What the button next to a step does when it is pressed. */
enum class SetupAction {
    ENABLE_DUTY,
    NOTIFICATION_ACCESS,
    BATTERY,
    CHAT,
    KEYWORDS,
    ALL_CLEAR,
}

/**
 * @param blocking nothing will ring until this is done; the rest only make the watch coarse.
 */
data class SetupStep(
    val action: SetupAction,
    val title: String,
    val button: String,
    val blocking: Boolean,
)

/**
 * What is still missing before this phone can be trusted to wake someone.
 *
 * A checklist that only names the problem makes the user hunt for the screen that fixes it, so
 * every step carries the action instead. Pure: the wording and the order are covered by tests,
 * and the order is the order of damage -- what stops the alarm entirely comes before what only
 * makes it fire too often.
 */
object SetupChecklist {

    fun steps(
        settings: AlertSettings,
        notificationAccess: Boolean,
        batteryFree: Boolean,
    ): List<SetupStep> = buildList {
        if (!settings.enabled) {
            add(
                SetupStep(
                    action = SetupAction.ENABLE_DUTY,
                    title = "Дежурство выключено — сейчас не слушает никто",
                    button = "Включить",
                    blocking = true,
                )
            )
        }
        if (!notificationAccess) {
            add(
                SetupStep(
                    action = SetupAction.NOTIFICATION_ACCESS,
                    title = "Нет доступа к уведомлениям — сообщения МАКСа не видны",
                    button = "Выдать",
                    blocking = true,
                )
            )
        }
        if (!batteryFree) {
            add(
                SetupStep(
                    action = SetupAction.BATTERY,
                    title = "Батарея ограничивает фон — служба не доживёт до ночи",
                    button = "Разрешить",
                    blocking = true,
                )
            )
        }
        if (settings.chatFilter.isBlank()) {
            add(
                SetupStep(
                    action = SetupAction.CHAT,
                    title = "Чат не выбран — тревогу поднимет сообщение из любого",
                    button = "Выбрать",
                    blocking = false,
                )
            )
        }
        val noWords = settings.keywords.isEmpty() &&
            settings.yellowHighKeywords.isEmpty() &&
            settings.yellowKeywords.isEmpty()
        if (noWords) {
            add(
                SetupStep(
                    action = SetupAction.KEYWORDS,
                    title = "Слова не заданы — звенит любое сообщение из чата",
                    button = "Задать",
                    blocking = false,
                )
            )
        }
        if (settings.deactivationKeywords.isEmpty()) {
            add(
                SetupStep(
                    action = SetupAction.ALL_CLEAR,
                    title = "Нет слов отбоя — тревогу придётся снимать вручную",
                    button = "Задать",
                    blocking = false,
                )
            )
        }
    }
}

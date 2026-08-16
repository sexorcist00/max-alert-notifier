package ru.maxalert.notifier

/**
 * Is this phone actually able to hear an alarm right now?
 *
 * The honest answer is not a boolean: one source can be dead while the other still works.
 * Kept as a pure function so the traffic light can be tested without a device.
 */
object SessionStatus {

    enum class Level { OK, WARN, FAIL }

    data class Status(val level: Level, val title: String, val detail: String)

    private const val STALE_MINUTES = 15L

    fun evaluate(
        watching: Boolean,
        notificationAccess: Boolean,
        directEnabled: Boolean,
        loggedIn: Boolean,
        online: Boolean,
        lastOnlineAt: Long,
        now: Long,
        /** MAX took the SMS code and is waiting for the account password. */
        awaitingPassword: Boolean = false,
    ): Status {
        if (!watching) {
            return Status(Level.FAIL, "Дежурство выключено", "Тревога не сработает ни при каких условиях")
        }

        val directWorking = directEnabled && loggedIn && online
        val directPossible = directEnabled && loggedIn

        if (!notificationAccess && !directPossible) {
            return Status(
                Level.FAIL,
                "Нет ни одного источника",
                "Выдайте доступ к уведомлениям или войдите в аккаунт MAX",
            )
        }

        if (directWorking && notificationAccess) {
            return Status(Level.OK, "На связи, оба источника", "Своё подключение к MAX + уведомления МАКСа")
        }

        if (directWorking) {
            return Status(
                Level.WARN,
                "На связи, но источник один",
                "Своё подключение работает; доступ к уведомлениям не выдан",
            )
        }

        if (directPossible) {
            val silence = if (lastOnlineAt > 0) minutesSince(lastOnlineAt, now) else null
            val detail = when {
                silence == null -> "Подключение ещё не устанавливалось"
                silence < STALE_MINUTES -> "Последний контакт $silence мин назад, идёт переподключение"
                else -> "Связи нет $silence мин — проверьте сеть или VPN"
            }
            val level = if (notificationAccess) Level.WARN else Level.FAIL
            return Status(level, "Нет связи с MAX", detail)
        }

        if (directEnabled && !loggedIn && awaitingPassword) {
            // Half-way through a login is not the same as never having tried: saying "вход не
            // выполнен" here sends the user back to the SMS step they already passed.
            return Status(
                Level.WARN,
                "MAX ждёт пароль от аккаунта",
                "Код принят — введите пароль в «Источник 2», вход на этом закончится",
            )
        }

        if (directEnabled && !loggedIn) {
            return Status(
                Level.WARN,
                "Вход в аккаунт не выполнен",
                if (notificationAccess) "Работают только уведомления МАКСа"
                else "Нет ни одного рабочего источника",
            )
        }

        return Status(
            Level.WARN,
            "Только уведомления МАКСа",
            "Если МАКС не покажет уведомление, тревоги не будет",
        )
    }

    private fun minutesSince(then: Long, now: Long): Long =
        ((now - then).coerceAtLeast(0L)) / 60_000L
}

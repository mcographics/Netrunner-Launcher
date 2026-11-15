package ohi.andre.consolelauncher.managers.xml.options

import ohi.andre.consolelauncher.managers.notifications.NotificationManager
import ohi.andre.consolelauncher.managers.xml.classes.XMLPrefsElement
import ohi.andre.consolelauncher.managers.xml.classes.XMLPrefsSave

enum class Notifications(
    private val defaultValue: String,
    private val info: String,
    private val type: String = XMLPrefsSave.BOOLEAN
) : XMLPrefsSave {
    show_notifications("false", "If true, Re:T-UI will show every incoming notification"),
    terminal_notifications("true", "If true, notifications will be printed in the terminal"),
    app_notification_enabled_default(
        "true",
        "If true, Re:T-UI will show notifications from all apps, unless they are explicitly excluded. If false, Re:T-UI won't show a notification from a specific app unless it was \texplicitly included"
    ),
    notification_text_color("#ffff1b1b", "Notification text color", XMLPrefsSave.COLOR),
    notification_format(
        "[%t] %pkg: %[#ffff1b1b]title --- %[#ffff1b1b]text",
        "The default format",
        XMLPrefsSave.TEXT
    ),
    click_notification(
        "true",
        "If true, Re:T-UI will perform the operation associated with the original notification when you click it"
    ),
    long_click_notification(
        "true",
        "If true, you will be able to perform some quick operations long-clicking a notification"
    ),
    notification_popup_exclude_app(
        "true",
        "If false, the \"Exclude app\" option won't be shown in the long click popup menu"
    ),
    notification_popup_exclude_notification(
        "true",
        "If false, the \"Exclude notification\" option won't be shown in the long click popup menu"
    ),
    notification_popup_reply(
        "true",
        "If false, the \"Reply to the last notification\" option won't be shown in the long click popup menu"
    );

    override fun defaultValue(): String = defaultValue

    override fun info(): String = info

    override fun parent(): XMLPrefsElement? = NotificationManager.instance

    override fun label(): String = name

    override fun type(): String = type

    override fun invalidValues(): Array<String>? = null

    override fun getLowercaseString(): String = label()

    override fun getString(): String = label()
}

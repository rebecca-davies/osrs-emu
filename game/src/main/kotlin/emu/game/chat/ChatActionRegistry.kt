package emu.game.chat

/** One non-suspending content action for an admitted chat input. */
fun interface ChatAction<T : ChatInput> {
    fun handle(input: T)
}

/** Immutable, declarative chat-input dispatch table analogous to the interface button registry. */
class ChatActionRegistry internal constructor(
    private val publicMessage: ChatAction<PublicChatInput>?,
    private val filterSettings: ChatAction<ChatFilterInput>?,
) {
    fun dispatch(input: ChatInput): Boolean =
        when (input) {
            is PublicChatInput -> publicMessage?.let { it.handle(input); true } ?: false
            is ChatFilterInput -> filterSettings?.let { it.handle(input); true } ?: false
        }
}

/** Declarative chat-content registration entry point. */
fun chatActions(init: ChatActionRegistryBuilder.() -> Unit): ChatActionRegistry =
    ChatActionRegistryBuilder().apply(init).build()

class ChatActionRegistryBuilder {
    private var publicMessage: ChatAction<PublicChatInput>? = null
    private var filterSettings: ChatAction<ChatFilterInput>? = null

    fun onPublicMessage(action: ChatAction<PublicChatInput>) {
        require(publicMessage == null) { "public chat action already registered" }
        publicMessage = action
    }

    fun onFilterSettings(action: ChatAction<ChatFilterInput>) {
        require(filterSettings == null) { "chat filter action already registered" }
        filterSettings = action
    }

    internal fun build(): ChatActionRegistry = ChatActionRegistry(publicMessage, filterSettings)
}

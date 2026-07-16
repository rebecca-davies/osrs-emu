package emu.transport.pipeline.handler

import emu.transport.message.IncomingMessage
import emu.transport.message.OutgoingMessage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

private data class Foo(val n: Int) : IncomingMessage
private data class Bar(val n: Int) : IncomingMessage
private data class Unbound(val n: Int) : IncomingMessage
private data class Echo(val n: Int) : OutgoingMessage

private class RecordingContext : HandlerContext {
    val written = mutableListOf<OutgoingMessage>()
    override suspend fun write(message: OutgoingMessage) {
        written += message
    }
}

class HandlerRepositoryTest {
    @Test fun `dispatch routes to the handler bound for the message's type`() = runBlocking {
        val seen = mutableListOf<String>()
        val repo = HandlerRepositoryBuilder()
            .bind(Foo::class.java) { message, _ -> seen += "foo:${message.n}" }
            .bind(Bar::class.java) { message, _ -> seen += "bar:${message.n}" }
            .build()
        val ctx = RecordingContext()

        repo.dispatch(Foo(1), ctx)
        repo.dispatch(Bar(2), ctx)

        assertEquals(listOf("foo:1", "bar:2"), seen)
    }

    @Test fun `unknown message type is a no-op`() = runBlocking {
        val repo = HandlerRepositoryBuilder()
            .bind(Foo::class.java) { _, _ -> error("must not be called") }
            .build()
        val ctx = RecordingContext()

        repo.dispatch(Unbound(1), ctx)

        assertTrue(ctx.written.isEmpty())
    }

    @Test fun `duplicate bind for the same type is rejected`() {
        val builder = HandlerRepositoryBuilder().bind(Foo::class.java) { _, _ -> }
        assertFailsWith<IllegalArgumentException> {
            builder.bind(Foo::class.java) { _, _ -> }
        }
    }

    @Test fun `rejected duplicate leaves the original handler intact`() = runBlocking {
        val seen = mutableListOf<String>()
        val builder = HandlerRepositoryBuilder()
            .bind(Foo::class.java) { message, _ -> seen += "original:${message.n}" }

        assertFailsWith<IllegalArgumentException> {
            builder.bind(Foo::class.java) { message, _ -> seen += "replacement:${message.n}" }
        }

        builder.build().dispatch(Foo(7), RecordingContext())
        assertEquals(listOf("original:7"), seen)
    }

    @Test fun `a handler can write output through the context`() = runBlocking {
        val repo = HandlerRepositoryBuilder()
            .bind(Foo::class.java, MessageHandler<Foo> { message, ctx -> ctx.write(Echo(message.n * 2)) })
            .build()
        val ctx = RecordingContext()

        repo.dispatch(Foo(21), ctx)

        assertEquals(listOf<OutgoingMessage>(Echo(42)), ctx.written)
    }
}

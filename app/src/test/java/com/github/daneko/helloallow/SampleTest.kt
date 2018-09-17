package com.github.daneko.helloallow

import arrow.core.*
import arrow.data.k
import arrow.data.sequence
import arrow.effects.*
import arrow.effects.typeclasses.bindingCancellable
import arrow.typeclasses.bindingCatch
import kotlinx.coroutines.experimental.Unconfined
import org.junit.Assert.*
import org.junit.Test

/**
 */
class SampleTest {

    companion object {
        val errorMsg: String = "／(^o^)＼"
    }

    fun throwError(): Int {
        throw Exception(errorMsg)
    }

    @Test
    fun useBindingCancellable() {

        try {
            val (res1, disposable1) = IO.async().run {
                bindingCancellable {
                    continueOn(Unconfined)
                    val a = IO.just(1).bind()
                    val b = throwError()
                    a + b
                }
            }

            res1.fix().attempt().unsafeRunSync().fold(
                    {
                        assertTrue(it.message == errorMsg)
                    },
                    {
                        fail()
                    }
            )

            val (res2, disposable2) = DeferredK.async().run {
                bindingCancellable {
                    continueOn(Unconfined)
                    val a = DeferredK.just(1).bind()
                    val b = throwError()
                    a + b
                }
            }

            res2.fix().unsafeRunAsync {
                it.fold(
                        {
                            assertTrue(it.message == errorMsg)
                        },
                        {
                            fail()
                        }
                )
            }

        } catch (e: Throwable) {
            fail()
        }

    }

    @Test
    fun useBindingCatch() {

        try {
            val res1 = Either.monadError<Throwable>().bindingCatch {
                val a = Either.right(1).bind()
                val b = throwError()
                a + b
            }.fix()

            res1.fold(
                    {
                        assertTrue(it.message == errorMsg)
                    },
                    {
                        fail()
                    }
            )

            val res2 = Try.monadError().bindingCatch {
                val a = Try.just(1).bind()
                val b = throwError()
                a + b
            }.fix()

            res2.fold(
                    {
                        assertTrue(it.message == errorMsg)
                    },
                    {
                        fail()
                    }
            )

        } catch (e: Throwable) {
            fail()
        }

    }

    @Test
    fun sequenceSample() {
        val src = listOf(1, 2, 3, 4)
        val target = src.map { (it % 2 == 0).maybe { it } }

        val actual = target.k().sequence(Option.applicative()).fix()

        assertEquals(actual, None)

        val target2 = src.map {
            Try {
                if (it % 2 == 0) it
                else throw Exception("$it")
            }
        }

        val actual2 = target2.k().sequence(Try.applicative()).fix()

        actual2.fold(
                {
                    assertTrue(it.message == "1")
                },
                {
                    fail()
                })
    }
}

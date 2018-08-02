package com.github.daneko.helloallow

import android.annotation.SuppressLint
import android.os.Looper
import android.util.Log
import androidx.lifecycle.ViewModel
import arrow.core.Try
import arrow.effects.*
import arrow.typeclasses.binding
import io.reactivex.Single
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import kotlinx.coroutines.experimental.*
import kotlinx.coroutines.experimental.android.UI
import kotlin.coroutines.experimental.CoroutineContext

class MainViewModel : ViewModel() {

    fun tooSlowMethod(): Result {
        if (isMainThread()) throw RuntimeException("＼(^o^)／")
        Thread.sleep(2000)
        return Result()
    }

    fun isMainThread() = Looper.myLooper() == Looper.getMainLooper()

    fun successProcess(result: Result) {
        if (!isMainThread()) throw RuntimeException("＼(^o^)／")
        Log.d("test", "success $result")
    }

    fun errorProcess(e: Throwable) {
        if (!isMainThread()) throw RuntimeException("＼(^o^)／")
        Log.d("test", "error", e)
    }

    fun slowMethodA(): ResultA {
        if (isMainThread()) throw RuntimeException("＼(^o^)／")
        Log.d("test", "A running thread is ${Thread.currentThread()}")
        Thread.sleep(2000)
        return ResultA()
    }

    fun slowMethodB(): ResultB {
        if (isMainThread()) throw RuntimeException("＼(^o^)／")
        Log.d("test", "B running thread is ${Thread.currentThread()}")
        Thread.sleep(2000)
        return ResultB()
    }

    fun makeResult(a: ResultA, b: ResultB): Result {
        Log.d("test", "makeResult running thread is ${Thread.currentThread()}")
        return Result()
    }

    @SuppressLint("CheckResult")
    fun callTooSlowMethodViaRx() {
        Single.create<Result> { emitter ->
            try {
                emitter.onSuccess(tooSlowMethod())
            } catch (e: Exception) {
                emitter.onError(e)
            }
        }
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    successProcess(it)
                }, {
                    errorProcess(it)
                })
    }

    fun callTooSlowMethodViaAsync() {
        launch(UI) {
            try {
                val res = withContext(DefaultDispatcher) { tooSlowMethod() }
                successProcess(res)
            } catch (e: Exception) {
                errorProcess(e)
            }
        }
    }

    fun callTooSlowMethodViaArrow() {
        DeferredK {
            tooSlowMethod()
        }.unsafeRunAsyncWrapper(UI,
                {
                    errorProcess(it)
                },
                {
                    successProcess(it)
                })
    }

    fun run1Wrapper() {
        run1().unsafeRunAsync { }
    }

    // DeferredKによって作られたThread上で動く
    fun run1(): DeferredK<Result> {
        return DeferredK {
            Log.d("test", "pre a running thread is ${Thread.currentThread()}")
            val a = slowMethodA()

            Log.d("test", "pre b running thread is ${Thread.currentThread()}")
            val b = slowMethodB()

            Log.d("test", "pre makeResult running thread is ${Thread.currentThread()}")
            makeResult(a, b)
        }
    }

    fun run2Wrapper() {
        run2().unsafeRunAsync { }
    }

    // 呼び出し側で作られたThreadで始まり、DeferredKで作られたThreadに切り替わりながら動く
    fun run2(): DeferredK<Result> =
            ForDeferredK extensions {
                binding {
                    Log.d("test", "pre a running thread is ${Thread.currentThread()}")
                    val a = DeferredK { slowMethodA() }.bind()

                    Log.d("test", "pre b running thread is ${Thread.currentThread()}")
                    val b = DeferredK { slowMethodB() }.bind()

                    Log.d("test", "pre makeResult running thread is ${Thread.currentThread()}")
                    makeResult(a, b)
                }.fix()
            }

    fun run3Wrapper() {
        launch { run3().unsafeRunAsync { } }
    }

    // 呼び出し側がmain threadだと死ぬ
    fun run3(): DeferredK<Result> =
            ForDeferredK extensions {
                binding {
                    Log.d("test", "pre a running thread is ${Thread.currentThread()}")
                    val a = DeferredK.just(slowMethodA()).bind()

                    Log.d("test", "pre b running thread is ${Thread.currentThread()}")
                    val b = DeferredK.just(slowMethodB()).bind()

                    Log.d("test", "pre makeResult running thread is ${Thread.currentThread()}")
                    makeResult(a, b)
                }.fix()
            }

    fun run4Wapper() {
        run4().unsafeRunAsync { }
    }

    // bindingの中身は呼び出し側だが、各slowMethod内はCommonPoolを利用したものになる
    fun run4(): DeferredK<Result> =
            ForDeferredK extensions {
                binding {
                    Log.d("test", "pre a running thread is ${Thread.currentThread()}")
                    val a = { DeferredK.just(slowMethodA()) }.bindIn(CommonPool).bind()

                    Log.d("test", "pre b running thread is ${Thread.currentThread()}")
                    val b = { DeferredK.just(slowMethodB()) }.bindIn(CommonPool).bind()

                    Log.d("test", "pre makeResult running thread is ${Thread.currentThread()}")
                    makeResult(a, b)
                }.fix()
            }

    fun runParallels1Wrapper() {
        runParallels1().unsafeRunAsync { }
    }

    fun runParallels1(): DeferredK<Result> {
        val a = DeferredK { slowMethodA() }
        val b = DeferredK { slowMethodB() }
        return a.flatMap { aa -> b.map { makeResult(aa, it) } }
    }

    fun runParallels2Wrapper() {
        runParallels2().unsafeRunAsync { }
    }

    fun runParallels2(): DeferredK<Result> =
            ForDeferredK extensions {
                binding {
                    val a: DeferredK<ResultA> = DeferredK { slowMethodA() }
                    val b: DeferredK<ResultB> = DeferredK { slowMethodB() }
                    makeResult(a.bind(), b.bind())
                }.fix()
            }
}

class Result

class ResultA

class ResultB

/**
 * 実装はunsafeAsyncRunとほぼ同じだが、Callback時のRunContextを選択できるようにしている
 * どうせ今回のケースだとEitherに対してfoldする(偏見)し、そこも展開してみた
 * Eitherに対してパターンマッチで…の場合もあるかもなので、お好みで
 */
fun <A> DeferredKOf<A>.unsafeRunAsyncWrapper(runContext: CoroutineContext, ifError: (Throwable) -> Unit, ifSuccess: (A) -> Unit) {
    async(Unconfined, CoroutineStart.DEFAULT) {
        Try { await() }.fold(
                { withContext(runContext) { ifError(it) } },
                { withContext(runContext) { ifSuccess(it) } })
    }.let {
        it.invokeOnCompletion { a: Throwable? ->
            if (a != null) throw a
        }
    }
}

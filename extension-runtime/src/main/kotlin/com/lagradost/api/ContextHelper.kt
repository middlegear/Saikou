package com.lagradost.api

import java.lang.ref.WeakReference

private var context: WeakReference<Any>? = null

fun setContext(ctx: WeakReference<Any>) {
    context = ctx
}

fun getContext(): Any? {
    return context?.get()
}

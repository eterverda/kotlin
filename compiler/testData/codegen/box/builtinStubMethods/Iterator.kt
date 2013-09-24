class MyIterator<TT>(val v: TT): Iterator<TT> {
    override fun next(): TT = v
    override fun hasNext(): Boolean = true
}

fun box(): String {
    try {
        (MyIterator<String>("") as MutableIterator<String>).remove()
        throw AssertionError()
    } catch (e: UnsupportedOperationException) {
        return "OK"
    }
}
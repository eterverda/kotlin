class MyList<TT>: List<TT> {
    override fun size(): Int = 0
    override fun isEmpty(): Boolean = true
    override fun contains(o: Any?): Boolean = false
    override fun iterator(): Iterator<TT> = throw UnsupportedOperationException()
    override fun toArray(): Array<Any?> = throw UnsupportedOperationException()
    override fun <E> toArray(a: Array<out E>): Array<E> = throw UnsupportedOperationException()
    override fun containsAll(c: Collection<Any?>): Boolean = false
    override fun get(index: Int): TT = throw IndexOutOfBoundsException()
    override fun indexOf(o: Any?): Int = -1
    override fun lastIndexOf(o: Any?): Int = -1
    override fun listIterator(): ListIterator<TT> = throw UnsupportedOperationException()
    override fun listIterator(index: Int): ListIterator<TT> = throw UnsupportedOperationException()
    override fun subList(fromIndex: Int, toIndex: Int): List<TT> = this
    override fun hashCode(): Int = 0
    override fun equals(other: Any?): Boolean = false
}

fun expectUoe(block: () -> Any) {
    try {
        block()
        throw AssertionError()
    } catch (e: UnsupportedOperationException) {
    }
}

fun box(): String {
    val list = MyList<String>() as MutableList<String>

    expectUoe { list.add("") }
    expectUoe { list.remove("") }
    expectUoe { list.addAll(list) }
    expectUoe { list.removeAll(list) }
    expectUoe { list.retainAll(list) }
    expectUoe { list.clear() }
    expectUoe { list.set(0, "") }
    expectUoe { list.add(0, "") }
    expectUoe { list.remove(0) }

    return "OK"
}
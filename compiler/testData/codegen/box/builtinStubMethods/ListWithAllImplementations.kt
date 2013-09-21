class MyList<TT>(val v: TT): List<TT> {
    override fun size(): Int = 0
    override fun isEmpty(): Boolean = true
    override fun contains(o: Any?): Boolean = false
    override fun iterator(): Iterator<TT> = throw UnsupportedOperationException()
    override fun toArray(): Array<Any?> = throw UnsupportedOperationException()
    override fun <E> toArray(a: Array<out E>): Array<E> = throw UnsupportedOperationException()
    override fun containsAll(c: Collection<Any?>): Boolean = false
    override fun get(index: Int): TT = v
    override fun indexOf(o: Any?): Int = -1
    override fun lastIndexOf(o: Any?): Int = -1
    override fun listIterator(): ListIterator<TT> = throw UnsupportedOperationException()
    override fun listIterator(index: Int): ListIterator<TT> = throw UnsupportedOperationException()
    override fun subList(fromIndex: Int, toIndex: Int): List<TT> = throw UnsupportedOperationException()
    override fun hashCode(): Int = 0
    override fun equals(other: Any?): Boolean = false

    public fun add(e: TT): Boolean = true
    public fun remove(o: Any?): Boolean = true
    public fun addAll(c: Collection<TT>): Boolean = true
    public fun addAll(index: Int, c: Collection<TT>): Boolean = true
    public fun removeAll(c: Collection<Any?>): Boolean = true
    public fun retainAll(c: Collection<Any?>): Boolean = true
    public fun clear() {}
    public fun set(index: Int, element: TT): TT = element
    public fun add(index: Int, element: TT) {}
    public fun remove(index: Int): TT = v
}

fun box(): String {
    val list = MyList<String>("") as MutableList<String>

    list.add("")
    list.remove("")
    list.addAll(list)
    list.removeAll(list)
    list.retainAll(list)
    list.clear()
    list.set(0, "")
    list.add(0, "")
    list.remove(0)

    return "OK"
}
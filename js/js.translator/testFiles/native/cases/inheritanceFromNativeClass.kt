package foo

native
open class A(b: Int) {
    native
    fun g(): Int = 0 // 2 * b

    native
    fun m(): Int = 0// b - 1
}

class B(val b: Int) : A(b / 2)

fun box(): String {

    val b = B(10)
    if (b !is A) return "b !is A"
    if (b.g() != 10) return "b.g() != 10, it: ${b.g()}"
    if (b.m() != 4) return "b.m() != 4, it: ${b.m()}"

    return "OK"
}
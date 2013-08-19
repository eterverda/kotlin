fun foo(x: Any?) {
    if (x is String) {
        object {
            fun bar() = x.length()
        }
    }
}
package test;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

public interface PrimitiveValueInParam {

    public @interface Ann {
        int i();
        short s();
        byte b();
        long l();
        double d();
        float f();
        boolean bool();
        char c();
        String str();
    }

    @Ann(
            i = 1,
            s = 2,
            b = 3,
            l = 4l,
            d = 5.0,
            f = 6f,
            bool = true,
            c = 'c',
            str = "str"
    )
    class A { }
}

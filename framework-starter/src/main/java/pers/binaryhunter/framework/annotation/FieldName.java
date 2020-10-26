package pers.binaryhunter.framework.annotation;

import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
@Inherited
@Target({ElementType.FIELD, ElementType.PARAMETER})
public @interface FieldName {
    String value() default "";
}

package io.github.b077as.smartjp.model.dto;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * "This component is never null": Jolt reads any annotation called NonNull on a record component at run time and then
 * writes {@code name: string} instead of {@code name?: string | null} in frontend/src/jolt-api.d.ts. Lombok's own @NonNull
 * does not work for this: it is not kept in the compiled class.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.RECORD_COMPONENT, ElementType.FIELD, ElementType.PARAMETER, ElementType.METHOD})
public @interface NonNull {
}

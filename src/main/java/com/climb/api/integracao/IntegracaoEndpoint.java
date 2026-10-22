package com.climb.api.integracao;

import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME) @Target(ElementType.METHOD)
public @interface IntegracaoEndpoint {
    String codigo();
    IntegracaoModulo modulo();
    String descricao();
    String exemplo() default "";
    String resposta() default "";
}

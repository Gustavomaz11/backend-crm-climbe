package com.climb.api.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.*;

@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = PropostaStatusValidator.class)
@Documented
public @interface ValidPropostaStatus {
    String message() default "Status inválido. Status permitidos: PENDENTE, APROVADA, REJEITADA";
    
    Class<?>[] groups() default {};
    
    Class<? extends Payload>[] payload() default {};
}

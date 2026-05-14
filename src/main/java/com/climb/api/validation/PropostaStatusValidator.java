package com.climb.api.validation;

import com.climb.api.model.enums.PropostaStatus;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class PropostaStatusValidator implements ConstraintValidator<ValidPropostaStatus, PropostaStatus> {
    
    @Override
    public void initialize(ValidPropostaStatus constraintAnnotation) {
        // Inicialização se necessário
    }
    
    @Override
    public boolean isValid(PropostaStatus status, ConstraintValidatorContext context) {
        // Null é permitido (será validado por @NotNull se necessário)
        if (status == null) {
            return true;
        }
        
        // Valida se é um dos status permitidos do enum PropostaStatus
        for (PropostaStatus s : PropostaStatus.values()) {
            if (s.equals(status)) {
                return true;
            }
        }
        
        return false;
    }
}

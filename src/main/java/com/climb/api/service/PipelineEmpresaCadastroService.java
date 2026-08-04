package com.climb.api.service;

import com.climb.api.model.Empresa;
import com.climb.api.model.dto.PipelineNegocioRequestDTO;
import com.climb.api.repository.EmpresaRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Component
class PipelineEmpresaCadastroService {
    private final EmpresaRepository empresaRepository;

    PipelineEmpresaCadastroService(EmpresaRepository empresaRepository) {
        this.empresaRepository = empresaRepository;
    }

    Empresa cadastrar(PipelineNegocioRequestDTO dto) {
        String cnpj = formatarCnpj(dto.cnpj());
        validarCnpj(cnpj);
        String cnpjNumerico = somenteDigitos(cnpj);
        if (empresaRepository.findFirstByCnpjIn(List.of(cnpj, cnpjNumerico)).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "CNPJ já cadastrado");
        }

        Empresa empresa = new Empresa();
        empresa.setRazaoSocial(dto.nomeEmpresa().trim());
        empresa.setNomeFantasia(dto.nomeEmpresa().trim());
        empresa.setCnpj(cnpj);
        empresa.setTelefone(dto.telefone().trim());
        empresa.setEmail(dto.email().trim().toLowerCase());
        empresa.setRepresentanteNome(dto.nomeContato().trim());
        empresa.setRepresentanteContato(dto.telefone().trim());

        try {
            return empresaRepository.saveAndFlush(empresa);
        } catch (DataIntegrityViolationException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "CNPJ já cadastrado", exception);
        }
    }

    private void validarCnpj(String cnpj) {
        String digits = somenteDigitos(cnpj);
        if (digits.length() != 14 || digits.chars().distinct().count() == 1) {
            throw cnpjInvalido();
        }
        int primeiro = calcularDigito(digits.substring(0, 12), new int[]{5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2});
        int segundo = calcularDigito(digits.substring(0, 13), new int[]{6, 5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2});
        if (primeiro != Character.digit(digits.charAt(12), 10)
                || segundo != Character.digit(digits.charAt(13), 10)) {
            throw cnpjInvalido();
        }
    }

    private int calcularDigito(String base, int[] fatores) {
        int soma = 0;
        for (int index = 0; index < fatores.length; index++) {
            soma += Character.digit(base.charAt(index), 10) * fatores[index];
        }
        int resto = soma % 11;
        return resto < 2 ? 0 : 11 - resto;
    }

    private String formatarCnpj(String valor) {
        String digits = somenteDigitos(valor);
        if (digits.length() != 14) throw cnpjInvalido();
        return "%s.%s.%s/%s-%s".formatted(
                digits.substring(0, 2), digits.substring(2, 5), digits.substring(5, 8),
                digits.substring(8, 12), digits.substring(12, 14)
        );
    }

    private String somenteDigitos(String valor) {
        return valor == null ? "" : valor.replaceAll("\\D", "");
    }

    private ResponseStatusException cnpjInvalido() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe um CNPJ válido para cadastrar a empresa");
    }
}

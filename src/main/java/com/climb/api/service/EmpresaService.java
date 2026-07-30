package com.climb.api.service;

import com.climb.api.mapper.EmpresaMapper;
import com.climb.api.model.Empresa;
import com.climb.api.model.dto.EmpresaRequestDTO;
import com.climb.api.model.dto.EmpresaResponseDTO;
import com.climb.api.repository.EmpresaRepository;

import org.springframework.http.HttpStatus;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class EmpresaService {

    private final EmpresaRepository repository;
    private final EmpresaMapper empresaMapper;

    public EmpresaService(EmpresaRepository repository, EmpresaMapper empresaMapper) {
        this.repository = repository;
        this.empresaMapper = empresaMapper;
    }

    public List<EmpresaResponseDTO> listar() {
        return empresaMapper.toResponseDto(repository.findAll());
    }

    public EmpresaResponseDTO buscarPorId(Long id) {
        Empresa empresa = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Empresa não encontrada"));
        return empresaMapper.toResponseDto(empresa);
    }

    public EmpresaResponseDTO criar(EmpresaRequestDTO empresaRequestDto) {

        if (repository.findByCnpj(empresaRequestDto.cnpj()).isPresent()) {
                throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "CNPJ já cadastrado"
                );
        }

        Empresa empresa = empresaMapper.toEntity(empresaRequestDto);
        return empresaMapper.toResponseDto(repository.save(empresa));
    }

    public EmpresaResponseDTO atualizar(Long id, EmpresaRequestDTO atualizada) {

        Empresa empresa = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Empresa não encontrada"));

        repository.findByCnpj(atualizada.cnpj()).ifPresent(e -> {
            if (!e.getIdEmpresa().equals(id)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "CNPJ já em uso");
            }
        });

        empresa.setRazaoSocial(atualizada.razaoSocial());
        empresa.setNomeFantasia(atualizada.nomeFantasia());
        empresa.setCnpj(atualizada.cnpj());
        empresa.setLogradouro(atualizada.logradouro());
        empresa.setNumero(atualizada.numero());
        empresa.setBairro(atualizada.bairro());
        empresa.setCidade(atualizada.cidade());
        empresa.setUf(atualizada.uf());
        empresa.setCep(atualizada.cep());
        empresa.setTelefone(atualizada.telefone());
        empresa.setEmail(atualizada.email());
        empresa.setRepresentanteNome(atualizada.representanteNome());
        empresa.setRepresentanteCpf(atualizada.representanteCpf());
        empresa.setRepresentanteContato(atualizada.representanteContato());

        return empresaMapper.toResponseDto(repository.save(empresa));
    }

    @Transactional
    public void deletar(Long id) {
        Empresa empresa = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Empresa não encontrada"));
        try {
            repository.delete(empresa);
            repository.flush();
        } catch (DataIntegrityViolationException exception) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "A empresa não pode ser excluída porque possui registros vinculados.",
                    exception
            );
        }
    }
}

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
    private final EmpresaComprovanteService comprovantes;

    public EmpresaService(EmpresaRepository repository, EmpresaMapper empresaMapper, EmpresaComprovanteService comprovantes) {
        this.repository = repository;
        this.empresaMapper = empresaMapper;
        this.comprovantes = comprovantes;
    }

    public List<EmpresaResponseDTO> listar() {
        return empresaMapper.toResponseDto(repository.findAll());
    }

    public EmpresaResponseDTO buscarPorId(Long id) {
        Empresa empresa = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Empresa não encontrada"));
        return empresaMapper.toResponseDto(empresa);
    }

    @Transactional
    public EmpresaResponseDTO criar(EmpresaRequestDTO empresaRequestDto) {

        if (repository.findByCnpj(empresaRequestDto.cnpj()).isPresent()) {
                throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "CNPJ já cadastrado"
                );
        }

        Empresa empresa = empresaMapper.toEntity(empresaRequestDto);
        empresa.setDadosCadastrais(CnpjaDadosCadastrais.semHistoricos(empresaRequestDto.dadosCadastrais()));
        empresa.setDadosCadastrais(CnpjaDadosCadastrais.comDadosBasicos(empresa));
        empresa = repository.save(empresa);
        comprovantes.agendar(empresa);
        return empresaMapper.toResponseDto(empresa);
    }

    @Transactional
    public EmpresaResponseDTO atualizar(Long id, EmpresaRequestDTO atualizada) {

        Empresa empresa = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Empresa não encontrada"));
        boolean mudouCnpj = !empresa.getCnpj().replaceAll("\\D", "").equals(atualizada.cnpj().replaceAll("\\D", ""));

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
        empresa.setTipoEmpresa(atualizada.tipoEmpresa());
        if (atualizada.dadosCadastrais() != null || mudouCnpj) empresa.setDadosCadastrais(CnpjaDadosCadastrais.semHistoricos(atualizada.dadosCadastrais()));
        empresa.setDadosCadastrais(CnpjaDadosCadastrais.comDadosBasicos(empresa));

        empresa = repository.save(empresa);
        comprovantes.agendar(empresa);
        return empresaMapper.toResponseDto(empresa);
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

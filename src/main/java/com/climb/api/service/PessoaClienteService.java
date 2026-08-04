package com.climb.api.service;

import com.climb.api.model.Empresa;
import com.climb.api.model.PessoaCliente;
import com.climb.api.model.dto.PessoaClienteRequestDTO;
import com.climb.api.model.dto.PessoaClienteResponseDTO;
import com.climb.api.model.dto.PessoaEmpresaResumoDTO;
import com.climb.api.repository.EmpresaRepository;
import com.climb.api.repository.PessoaClienteRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
public class PessoaClienteService {
    private final PessoaClienteRepository repository;
    private final EmpresaRepository empresaRepository;

    public PessoaClienteService(PessoaClienteRepository repository, EmpresaRepository empresaRepository) {
        this.repository = repository;
        this.empresaRepository = empresaRepository;
    }

    @Transactional(readOnly = true)
    public List<PessoaClienteResponseDTO> listar() {
        return repository.findAllByOrderByNomeAsc().stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public PessoaClienteResponseDTO buscar(Long id) {
        return toResponse(buscarEntidade(id));
    }

    @Transactional
    public PessoaClienteResponseDTO criar(PessoaClienteRequestDTO request) {
        validarCpfUnico(request.cpf(), null);
        LocalDateTime agora = LocalDateTime.now();
        PessoaCliente pessoa = new PessoaCliente();
        pessoa.setCriadoEm(agora);
        aplicarDados(pessoa, request, agora);
        return toResponse(repository.save(pessoa));
    }

    @Transactional
    public PessoaClienteResponseDTO atualizar(Long id, PessoaClienteRequestDTO request) {
        PessoaCliente pessoa = buscarEntidade(id);
        validarCpfUnico(request.cpf(), id);
        aplicarDados(pessoa, request, LocalDateTime.now());
        return toResponse(repository.save(pessoa));
    }

    @Transactional
    public void excluir(Long id) {
        repository.delete(buscarEntidade(id));
    }

    private void aplicarDados(PessoaCliente pessoa, PessoaClienteRequestDTO request, LocalDateTime atualizadoEm) {
        pessoa.setNome(request.nome().trim());
        pessoa.setCpf(normalizarCpf(request.cpf()));
        pessoa.setEmail(request.email().trim().toLowerCase());
        pessoa.setTelefone(request.telefone().trim());
        pessoa.setCargo(textoOpcional(request.cargo()));
        pessoa.setObservacoes(textoOpcional(request.observacoes()));
        pessoa.setAtivo(request.ativo() == null || request.ativo());
        pessoa.setEmpresas(buscarEmpresas(request.empresaIds()));
        pessoa.setAtualizadoEm(atualizadoEm);
    }

    private LinkedHashSet<Empresa> buscarEmpresas(Set<Long> empresaIds) {
        List<Empresa> empresas = empresaRepository.findAllById(empresaIds);
        if (empresas.size() != empresaIds.size()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Uma das empresas selecionadas não foi encontrada");
        }
        return new LinkedHashSet<>(empresas);
    }

    private void validarCpfUnico(String cpf, Long pessoaId) {
        String normalizado = normalizarCpf(cpf);
        if (normalizado == null) return;
        repository.findByCpf(normalizado).ifPresent(existente -> {
            if (!existente.getIdPessoa().equals(pessoaId)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "CPF já cadastrado para outra pessoa");
            }
        });
    }

    private PessoaCliente buscarEntidade(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Pessoa não encontrada"));
    }

    private String normalizarCpf(String cpf) {
        if (cpf == null || cpf.isBlank()) return null;
        String numeros = cpf.replaceAll("\\D", "");
        return numeros.replaceFirst("(\\d{3})(\\d{3})(\\d{3})(\\d{2})", "$1.$2.$3-$4");
    }

    private String textoOpcional(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }

    private PessoaClienteResponseDTO toResponse(PessoaCliente pessoa) {
        List<PessoaEmpresaResumoDTO> empresas = pessoa.getEmpresas().stream()
                .sorted((a, b) -> nomeEmpresa(a).compareToIgnoreCase(nomeEmpresa(b)))
                .map(empresa -> new PessoaEmpresaResumoDTO(
                        empresa.getIdEmpresa(), nomeEmpresa(empresa), empresa.getCnpj()))
                .toList();
        return new PessoaClienteResponseDTO(
                pessoa.getIdPessoa(), pessoa.getNome(), pessoa.getCpf(), pessoa.getEmail(), pessoa.getTelefone(),
                pessoa.getCargo(), pessoa.getObservacoes(), pessoa.getAtivo(), empresas,
                pessoa.getCriadoEm(), pessoa.getAtualizadoEm());
    }

    private String nomeEmpresa(Empresa empresa) {
        return empresa.getNomeFantasia() == null || empresa.getNomeFantasia().isBlank()
                ? empresa.getRazaoSocial()
                : empresa.getNomeFantasia();
    }
}

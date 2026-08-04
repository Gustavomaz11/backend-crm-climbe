package com.climb.api.service;

import com.climb.api.model.Empresa;
import com.climb.api.model.PessoaCliente;
import com.climb.api.model.dto.PessoaClienteRequestDTO;
import com.climb.api.repository.EmpresaRepository;
import com.climb.api.repository.PessoaClienteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PessoaClienteServiceTest {
    @Mock private PessoaClienteRepository repository;
    @Mock private EmpresaRepository empresaRepository;
    private PessoaClienteService service;

    @BeforeEach
    void setUp() {
        service = new PessoaClienteService(repository, empresaRepository);
    }

    @Test
    void deveCadastrarPessoaVinculadaAMultiplasEmpresas() {
        Empresa primeira = empresa(1L, "Climbe");
        Empresa segunda = empresa(2L, "Jota");
        PessoaClienteRequestDTO request = new PessoaClienteRequestDTO(
                "Maria Silva", "529.982.247-25", "maria@cliente.com", "11999999999",
                "Diretora Financeira", null, true, Set.of(1L, 2L));
        when(repository.findByCpf("529.982.247-25")).thenReturn(Optional.empty());
        when(empresaRepository.findAllById(Set.of(1L, 2L))).thenReturn(List.of(primeira, segunda));
        when(repository.save(any())).thenAnswer(invocation -> {
            PessoaCliente pessoa = invocation.getArgument(0);
            pessoa.setIdPessoa(10L);
            return pessoa;
        });

        var response = service.criar(request);

        assertEquals(10L, response.id());
        assertEquals(2, response.empresas().size());
        assertEquals(List.of("Climbe", "Jota"), response.empresas().stream().map(item -> item.nome()).toList());
    }

    private Empresa empresa(Long id, String nome) {
        Empresa empresa = new Empresa();
        empresa.setIdEmpresa(id);
        empresa.setNomeFantasia(nome);
        empresa.setRazaoSocial(nome + " LTDA");
        empresa.setCnpj("00.000.000/000" + id + "-00");
        return empresa;
    }
}

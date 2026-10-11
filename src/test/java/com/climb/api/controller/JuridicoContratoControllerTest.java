package com.climb.api.controller;

import com.climb.api.model.Contrato;
import com.climb.api.model.dto.KanbanArquivamentoRequestDTO;
import com.climb.api.model.dto.ContratoPreparacaoRequestDTO;
import com.climb.api.model.dto.ContratoResponsaveisRequestDTO;
import com.climb.api.model.dto.ContratoParcelaVencimentoRequestDTO;
import com.climb.api.model.enums.ContratoPreparacaoEtapa;
import com.climb.api.repository.*;
import com.climb.api.security.*;
import com.climb.api.service.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.multipart.MultipartFile;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class JuridicoContratoControllerTest {
    @Configuration @EnableMethodSecurity static class Seguranca {}
    @AfterEach void limpar() { SecurityContextHolder.clearContext(); }
    static Stream<String> acoes() {
        return Stream.of("LER", "CRIAR", "EDITAR", "DELETAR", "MOVIMENTAR", "ANEXAR_ARQUIVO", "ENVIAR_CLIENTE", "RESPONSAVEIS_EDITAR", "VENCIMENTOS_EDITAR", "APROVAR", "DESVINCULAR_PROPOSTA", "ARQUIVAR", "DESARQUIVAR");
    }

    @ParameterizedTest @MethodSource("acoes")
    void exigeExatamenteAPermissaoDaAcaoMesmoComOutrosAcessosJuridicos(String acao) {
        var usuarios = mock(UsuarioRepository.class);
        var rbac = new RbacService(usuarios);
        var service = mock(ContratoService.class);
        var auth = new UsernamePasswordAuthenticationToken("juridico@test.local", null, List.of());
        auth.setDetails(7L); SecurityContextHolder.getContext().setAuthentication(auth);
        try (var ctx = new AnnotationConfigApplicationContext()) {
            ctx.register(Seguranca.class);
            ctx.registerBean("permissaoAcesso", PermissaoAcesso.class, () -> new PermissaoAcesso(
                    new AuthenticatedUserProvider(), rbac, mock(ContratoRepository.class),
                    mock(ContratoEquipeService.class), mock(RevisaoDocumentoRepository.class)));
            ctx.registerBean(ContratoController.class, () -> new ContratoController(service));
            ctx.refresh();
            var controller = ctx.getBean(ContratoController.class);
            when(usuarios.findCodigosPermissoesById(7L)).thenReturn(acoes()
                    .filter(a -> !a.equals(acao)).map(a -> "JURIDICO_CONTRATO_" + a).toList());
            assertThatThrownBy(() -> executar(controller, acao)).isInstanceOf(AccessDeniedException.class);
            verifyNoInteractions(service);
            when(usuarios.findCodigosPermissoesById(7L)).thenReturn(List.of("JURIDICO_CONTRATO_" + acao));
            executar(controller, acao);
        }
    }

    private void executar(ContratoController c, String acao) {
        switch (acao) {
            case "LER" -> c.listar();
            case "CRIAR" -> c.criar(new Contrato());
            case "EDITAR" -> c.atualizar(1L, new Contrato());
            case "DELETAR" -> c.deletar(1L);
            case "MOVIMENTAR" -> c.moverPreparacao(1L, new ContratoPreparacaoRequestDTO(ContratoPreparacaoEtapa.EM_ANDAMENTO));
            case "ANEXAR_ARQUIVO" -> c.enviarPdf(1L, mock(MultipartFile.class));
            case "ENVIAR_CLIENTE" -> c.enviarAoCliente(1L, mock(MultipartFile.class));
            case "RESPONSAVEIS_EDITAR" -> c.atualizarResponsaveis(1L, new ContratoResponsaveisRequestDTO(7L, List.of(7L)));
            case "VENCIMENTOS_EDITAR" -> c.alterarVencimentoParcela(1L, 2L, new ContratoParcelaVencimentoRequestDTO(LocalDate.now()));
            case "APROVAR" -> c.aprovar(1L, Map.of("status", "APROVADO"));
            case "DESVINCULAR_PROPOSTA" -> c.desvincularProposta(1L);
            case "ARQUIVAR" -> c.alterarArquivamento(1L, new KanbanArquivamentoRequestDTO(true));
            case "DESARQUIVAR" -> c.alterarArquivamento(1L, new KanbanArquivamentoRequestDTO(false));
            default -> throw new IllegalArgumentException(acao);
        }
    }
}

package com.climb.api.controller;

import com.climb.api.model.Cargo;
import com.climb.api.model.Contrato;
import com.climb.api.model.dto.DocumentoLoteRequestDTO;
import com.climb.api.model.dto.DocumentoValidacaoRequestDTO;
import com.climb.api.model.dto.PropostaRequestDTO;
import com.climb.api.model.enums.PropostaStatus;
import com.climb.api.repository.*;
import com.climb.api.security.AuthenticatedUserProvider;
import com.climb.api.security.PermissaoAcesso;
import com.climb.api.service.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.multipart.MultipartFile;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Stream;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class PermissoesCrudControllerTest {
    @Configuration @EnableMethodSecurity static class Seguranca {}
    @AfterEach void limpar() { SecurityContextHolder.clearContext(); }

    static Stream<String> acoes() {
        return Stream.of("CONTRATO", "PROPOSTA", "DOCUMENTO_JURIDICO", "RELATORIO", "CARGO")
                .flatMap(r -> Stream.of("LER", "CRIAR", "EDITAR", "DELETAR")
                        // O catálogo de cargos também é público no formulário de cadastro.
                        .filter(a -> !r.equals("CARGO") || !a.equals("LER")).map(a -> r + "_" + a));
    }

    @ParameterizedTest @MethodSource("acoes")
    void autorizaSomenteAAcaoConcedidaMesmoChamandoAApiDiretamente(String codigo) {
        var usuarios = mock(UsuarioRepository.class);
        var rbac = new RbacService(usuarios);
        var contratoService = mock(ContratoService.class);
        var propostaService = mock(PropostaService.class);
        var documentoService = mock(DocumentoService.class);
        var relatorioService = mock(RelatorioService.class);
        var cargoService = mock(CargoService.class);
        var auth = new UsernamePasswordAuthenticationToken("usuario@test.local", null, List.of());
        auth.setDetails(7L); SecurityContextHolder.getContext().setAuthentication(auth);
        try (var context = new AnnotationConfigApplicationContext()) {
            context.register(Seguranca.class);
            context.registerBean("permissaoAcesso", PermissaoAcesso.class, () -> new PermissaoAcesso(
                    new AuthenticatedUserProvider(), rbac, mock(ContratoRepository.class),
                    mock(ContratoEquipeService.class), mock(RevisaoDocumentoRepository.class)));
            context.registerBean(ContratoController.class, () -> new ContratoController(contratoService));
            context.registerBean(PropostaController.class, () -> new PropostaController(propostaService, rbac));
            context.registerBean(DocumentoController.class, () -> new DocumentoController(documentoService));
            context.registerBean(RelatorioController.class, () -> new RelatorioController(relatorioService));
            context.registerBean(CargoController.class, () -> new CargoController(cargoService, rbac));
            context.refresh();
            String recurso = codigo.substring(0, codigo.lastIndexOf('_'));
            String acao = codigo.substring(codigo.lastIndexOf('_') + 1);
            for (String outra : List.of("LER", "CRIAR", "EDITAR", "DELETAR")) {
                if (outra.equals(acao)) continue;
                when(usuarios.findCodigosPermissoesById(7L)).thenReturn(List.of(recurso + "_" + outra));
                assertThatThrownBy(() -> executar(context, recurso, acao)).isInstanceOfAny(
                        org.springframework.security.access.AccessDeniedException.class,
                        org.springframework.web.server.ResponseStatusException.class);
                verifyNoInteractions(contratoService, propostaService, documentoService, relatorioService, cargoService);
            }
            when(usuarios.findCodigosPermissoesById(7L)).thenReturn(List.of(codigo));
            executar(context, recurso, acao);
        }
    }

    private void executar(AnnotationConfigApplicationContext ctx, String recurso, String acao) {
        switch (recurso) {
            case "CONTRATO" -> {
                var c = ctx.getBean(ContratoController.class);
                switch (acao) { case "LER" -> c.listar(); case "CRIAR" -> c.criar(new Contrato());
                    case "EDITAR" -> c.atualizar(1L, new Contrato()); case "DELETAR" -> c.deletar(1L); }
            }
            case "PROPOSTA" -> {
                var c = ctx.getBean(PropostaController.class);
                var dto = new PropostaRequestDTO(1L, null, 7L, null, BigDecimal.TEN, PropostaStatus.PENDENTE, LocalDate.now());
                switch (acao) { case "LER" -> c.listar(null, null); case "CRIAR" -> c.criar(dto);
                    case "EDITAR" -> c.atualizar(1L, dto); case "DELETAR" -> c.deletar(1L); }
            }
            case "DOCUMENTO_JURIDICO" -> {
                var c = ctx.getBean(DocumentoController.class);
                switch (acao) { case "LER" -> c.listar();
                    case "CRIAR" -> c.solicitarLote(new DocumentoLoteRequestDTO(1L, List.of("DRE"), "cliente@test.local"));
                    case "EDITAR" -> c.validar(1L, new DocumentoValidacaoRequestDTO(com.climb.api.model.enums.DocumentoStatus.APROVADO));
                    case "DELETAR" -> c.deletar(1L); }
            }
            case "RELATORIO" -> {
                var c = ctx.getBean(RelatorioController.class);
                switch (acao) { case "LER" -> c.listar(); case "CRIAR" -> c.uploadPdf(1L, mock(MultipartFile.class));
                    case "EDITAR" -> c.atualizar(1L, mock(MultipartFile.class)); case "DELETAR" -> c.deletar(1L); }
            }
            case "CARGO" -> {
                var c = ctx.getBean(CargoController.class);
                switch (acao) { case "CRIAR" -> c.criar(new Cargo()); case "EDITAR" -> c.atualizar(1L, new Cargo());
                    case "DELETAR" -> c.deletar(1L); }
            }
        }
    }
}

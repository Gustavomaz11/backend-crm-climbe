package com.climb.api.service;

import com.climb.api.config.GoogleDriveProperties;
import com.climb.api.model.*;
import com.climb.api.model.enums.ServicoComercial;
import com.climb.api.repository.ContratoRepository;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class DriveAcessoServiceTest {
    private final ContratoEquipeService equipes = mock(ContratoEquipeService.class);
    private final ContratoRepository contratos = mock(ContratoRepository.class);
    private final DriveAcessoService acesso = new DriveAcessoService(equipes, contratos, new GoogleDriveProperties());

    @Test void permiteApenasDominioExato() {
        assertThat(acesso.emailPermitido(" ANA@CLIMBE.COM.BR ")).isTrue();
        for (var email : List.of("ana@gmail.com", "ana@climbe.com.br.evil.test", "ana@sub.climbe.com.br", "@climbe.com.br", "ana@@climbe.com.br"))
            assertThat(acesso.emailPermitido(email)).as(email).isFalse();
    }

    @Test void compartilhaUniaoDosContratosAtivosDoMesmoServicoERetiraUsuariosInativos() {
        var primeiro = contrato(1L); var segundo = contrato(2L); var encerrado = contrato(3L);
        encerrado.getEncerramentosPorServico().put("CFO", new ContratoServicoEncerramento(LocalDateTime.now(), usuario(9L, "lider@climbe.com.br", "ATIVO")));
        when(contratos.findByEmpresa_IdEmpresaOrderByIdContratoDesc(100L)).thenReturn(List.of(primeiro, segundo, encerrado));
        when(equipes.membrosAtuais(primeiro, ServicoComercial.CFO)).thenReturn(List.of(
                usuario(1L, " ANA@CLIMBE.COM.BR ", "ATIVO"), usuario(2L, "bia@gmail.com", "ATIVO"), usuario(3L, "caio@climbe.com.br", "INATIVO")));
        when(equipes.membrosAtuais(segundo, ServicoComercial.CFO)).thenReturn(List.of(usuario(4L, "daniel@climbe.com.br", "ATIVO")));
        assertThat(acesso.emails(100L, ServicoComercial.CFO)).containsExactly("ana@climbe.com.br", "daniel@climbe.com.br");
        assertThatThrownBy(() -> acesso.exigir(primeiro, ServicoComercial.CONTABILIDADE, 1L)).hasMessageContaining("exclusivo");
        assertThatThrownBy(() -> acesso.exigir(primeiro, ServicoComercial.CFO, 3L)).hasMessageContaining("exclusivo");
    }
    private Contrato contrato(Long id) { var c = new Contrato(); c.setIdContrato(id); c.setServico(ServicoComercial.CFO); c.setStatus("APROVADO"); return c; }
    private Usuario usuario(Long id, String email, String situacao) { var u = new Usuario(); u.setId(id); u.setEmail(email); u.setSituacao(situacao); return u; }
}

package com.climb.api.service;

import com.climb.api.model.Contrato;
import com.climb.api.model.ContratoParcela;
import com.climb.api.model.Usuario;
import com.climb.api.model.dto.EmpresaFinanceiroResponseDTO;
import com.climb.api.repository.ContratoRepository;
import com.climb.api.repository.EmpresaRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class EmpresaFinanceiroService {
    private final EmpresaRepository empresaRepository;
    private final ContratoRepository contratoRepository;

    public EmpresaFinanceiroService(EmpresaRepository empresaRepository, ContratoRepository contratoRepository) {
        this.empresaRepository = empresaRepository;
        this.contratoRepository = contratoRepository;
    }

    @Transactional(readOnly = true)
    public EmpresaFinanceiroResponseDTO consultar(Long empresaId) {
        if (!empresaRepository.existsById(empresaId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Empresa não encontrada");
        }
        List<EmpresaFinanceiroResponseDTO.ServicoContratadoDTO> servicos = contratoRepository
                .findByEmpresa_IdEmpresaOrderByIdContratoDesc(empresaId).stream()
                .map(this::toServico)
                .toList();
        EmpresaFinanceiroResponseDTO.ParcelaDTO proxima = servicos.stream()
                .flatMap(item -> item.parcelas().stream())
                .filter(item -> "PENDENTE".equalsIgnoreCase(item.status()))
                .min(Comparator.comparing(EmpresaFinanceiroResponseDTO.ParcelaDTO::vencimento))
                .orElse(null);
        return new EmpresaFinanceiroResponseDTO(
                empresaId,
                proxima == null ? null : proxima.valor(),
                proxima == null ? null : proxima.vencimento(),
                servicos
        );
    }

    private EmpresaFinanceiroResponseDTO.ServicoContratadoDTO toServico(Contrato contrato) {
        List<EmpresaFinanceiroResponseDTO.ParcelaDTO> parcelas = contrato.getParcelas().stream()
                .map(item -> new EmpresaFinanceiroResponseDTO.ParcelaDTO(
                        item.getId(), item.getNumero(), item.getVencimento(), item.getValor(), item.getStatus()))
                .toList();
        EmpresaFinanceiroResponseDTO.ParcelaDTO proxima = parcelas.stream()
                .filter(item -> "PENDENTE".equalsIgnoreCase(item.status()))
                .min(Comparator.comparing(EmpresaFinanceiroResponseDTO.ParcelaDTO::vencimento))
                .orElse(null);
        BigDecimal total = parcelas.stream().map(EmpresaFinanceiroResponseDTO.ParcelaDTO::valor)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        Map<Long, Usuario> funcionarios = new LinkedHashMap<>();
        if (contrato.getResponsavel() != null) funcionarios.put(contrato.getResponsavel().getId(), contrato.getResponsavel());
        contrato.getParticipantes().forEach(item -> funcionarios.put(item.getId(), item));
        return new EmpresaFinanceiroResponseDTO.ServicoContratadoDTO(
                contrato.getIdContrato(), contrato.getServico(), situacao(contrato), total,
                proxima == null ? null : proxima.valor(), proxima == null ? null : proxima.vencimento(), parcelas,
                funcionarios.values().stream().map(item -> new EmpresaFinanceiroResponseDTO.FuncionarioDTO(
                        item.getId(), item.getNomeCompleto(), item.getEmail())).toList()
        );
    }

    private String situacao(Contrato contrato) {
        if ("APROVADO".equalsIgnoreCase(contrato.getStatus())
                && (contrato.getDataFim() == null || !contrato.getDataFim().isBefore(LocalDate.now()))) return "ATIVO";
        if (contrato.getDataFim() != null && contrato.getDataFim().isBefore(LocalDate.now())) return "ENCERRADO";
        if (List.of("ENCERRADO", "CANCELADO", "REJEITADO").contains(contrato.getStatus().toUpperCase())) return "ENCERRADO";
        return "PENDENTE";
    }
}

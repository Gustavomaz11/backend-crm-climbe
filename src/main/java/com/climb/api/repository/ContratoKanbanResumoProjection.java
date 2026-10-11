package com.climb.api.repository;

import com.climb.api.model.enums.ServicoComercial;

public interface ContratoKanbanResumoProjection {
    Long getId();
    String getEmpresaNome();
    String getUrlPdf();
    String getStatus();
    boolean getEquipeConfigurada();
    ServicoComercial getServico();
    ServicoComercial getServicoDaProposta();
}

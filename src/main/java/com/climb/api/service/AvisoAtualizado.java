package com.climb.api.service;

/** Apenas sinaliza quais dados devem ser sincronizados; não transporta conteúdo privado. */
public record AvisoAtualizado(Long usuarioId, Long reuniaoId, Tipo tipo) {
    public enum Tipo { NOTIFICACOES, CONVITES, KANBAN }
}

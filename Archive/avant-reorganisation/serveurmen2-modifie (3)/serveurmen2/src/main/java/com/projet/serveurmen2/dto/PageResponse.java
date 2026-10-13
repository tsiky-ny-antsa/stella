package com.projet.serveurmen2.dto;

import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

/**
 * Enveloppe generique de pagination renvoyee aux clients.
 * Evite d'exposer directement l'objet Page de Spring (stable et lisible en JSON).
 */
public record PageResponse<T>(
        List<T> contenu,
        int page,
        int taille,
        long totalElements,
        int totalPages,
        boolean premiere,
        boolean derniere) {

    public static <E, T> PageResponse<T> from(Page<E> p, Function<E, T> mapper) {
        return new PageResponse<>(
                p.getContent().stream().map(mapper).toList(),
                p.getNumber(),
                p.getSize(),
                p.getTotalElements(),
                p.getTotalPages(),
                p.isFirst(),
                p.isLast());
    }
}

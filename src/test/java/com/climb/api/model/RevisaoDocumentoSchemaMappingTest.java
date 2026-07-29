package com.climb.api.model;

import jakarta.persistence.Column;
import jakarta.persistence.Lob;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThat;

class RevisaoDocumentoSchemaMappingTest {

    @Test
    void camposTextuaisDevemCorresponderAoTipoTextCriadoPelaMigration() throws Exception {
        assertTextColumn(RevisaoDocumento.class, "justificativa");
        assertTextColumn(RevisaoDocumentoVersao.class, "comentarioGeral");
        assertTextColumn(RevisaoDocumentoVersao.class, "justificativa");
        assertTextColumn(RevisaoDocumentoAnotacao.class, "comentario");
    }

    private void assertTextColumn(Class<?> entityType, String fieldName) throws Exception {
        Field field = entityType.getDeclaredField(fieldName);
        Column column = field.getAnnotation(Column.class);

        assertThat(field.getAnnotation(Lob.class))
                .as("%s.%s não deve ser mapeado como CLOB", entityType.getSimpleName(), fieldName)
                .isNull();
        assertThat(column)
                .as("%s.%s deve declarar o tipo SQL", entityType.getSimpleName(), fieldName)
                .isNotNull();
        assertThat(column.columnDefinition())
                .as("%s.%s deve corresponder ao TEXT da V38", entityType.getSimpleName(), fieldName)
                .isEqualToIgnoringCase("TEXT");
    }
}

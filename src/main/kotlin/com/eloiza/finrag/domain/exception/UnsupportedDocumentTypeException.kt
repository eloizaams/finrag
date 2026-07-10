package com.eloiza.finrag.domain.exception

class UnsupportedDocumentTypeException(
    extension: String,
) : RuntimeException("Tipo de documento não suportado: .$extension")

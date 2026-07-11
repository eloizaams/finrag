package com.eloiza.finrag.domain.exception

// Mesma mensagem para "não existe" e "pertence a outro usuário": não revelar existência
class DocumentNotFoundException : RuntimeException("Documento não encontrado")

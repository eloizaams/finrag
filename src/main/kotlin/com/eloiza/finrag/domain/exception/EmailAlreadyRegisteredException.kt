package com.eloiza.finrag.domain.exception

class EmailAlreadyRegisteredException(
    email: String,
) : RuntimeException("Email já cadastrado: $email")

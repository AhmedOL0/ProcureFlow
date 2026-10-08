package com.procureflow.identity.application;

import com.procureflow.identity.domain.User;

/** Result of register/login/refresh: the user plus a usable token pair. */
public record AuthResult(User user, TokenPair tokens) {
}

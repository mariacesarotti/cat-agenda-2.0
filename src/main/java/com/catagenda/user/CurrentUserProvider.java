package com.catagenda.user;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

/**
 * Resolves the domain {@link User} behind the currently authenticated
 * session. Spring Security's {@link Authentication#getName()} only knows
 * the username (our email) it validated against — controllers use this to
 * get the actual {@link User} row so ownership-scoped services always have
 * a real owner to check against, never just an email string.
 */
@Component
public class CurrentUserProvider {

    private final UserRepository userRepository;

    public CurrentUserProvider(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public User get(Authentication authentication) {
        return userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new IllegalStateException(
                        "Authenticated session has no matching user: " + authentication.getName()));
    }
}

package com.catagenda.user;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Collections;

/**
 * Bridges our {@link User} entity to Spring Security, which knows nothing
 * about our schema and only understands its own {@link UserDetails}
 * contract (username, password hash, granted authorities, account-state
 * flags). Spring Security's login flow calls {@link #loadUserByUsername}
 * during authentication and compares the submitted password against the
 * hash this returns.
 */
@Service
public class CatAgendaUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    public CatAgendaUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("No account for " + email));

        return org.springframework.security.core.userdetails.User
                .withUsername(user.getEmail())
                .password(user.getPasswordHash())
                .authorities(Collections.emptyList())
                .build();
    }
}

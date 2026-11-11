package com.wsb.animaladoption.service;

import com.wsb.animaladoption.enums.RoleEnum;
import com.wsb.animaladoption.enums.UserTypeEnum;
import com.wsb.animaladoption.model.User;
import com.wsb.animaladoption.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates the first administrator account when the application starts.
 * <p>
 * A fresh installation has no administrator, and roles can only be given by an administrator
 * (on the user's profile), so the first one has to come from somewhere. Instead of a special endpoint
 * (which anyone on the internet could attack) it comes from the deployment configuration:
 * the environment variables {@code INITIAL_ADMIN_EMAIL} and {@code INITIAL_ADMIN_PASSWORD}.
 * <p>
 * Rules:
 * <ul>
 *     <li>it does anything only if the database has <b>no</b> administrator yet - so after the first start
 *     the variables can be removed, and changing them never touches existing accounts;</li>
 *     <li>if an account with this email already exists, it is promoted to administrator and its password
 *     is <b>not</b> changed (the owner already knows it);</li>
 *     <li>otherwise a new, already verified account is created with the given password.</li>
 * </ul>
 */
@Slf4j
@Component
public class InitialAdminCreator implements ApplicationRunner {
    /** Stricter than for ordinary users (8) - this account can do everything. */
    static final int MIN_PASSWORD_LENGTH = 12;
    /** Required by the database; the administrator can change it in the profile. */
    static final String PLACEHOLDER_MOBILE = "000000000";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final String email;
    private final String password;

    public InitialAdminCreator(UserRepository userRepository,
                               PasswordEncoder passwordEncoder,
                               @Value("${app.initial-admin.email:}") String email,
                               @Value("${app.initial-admin.password:}") String password) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.email = email == null ? "" : email.trim().toLowerCase();
        this.password = password == null ? "" : password;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (email.isEmpty()) {
            return;
        }
        if (userRepository.existsByRole(RoleEnum.ADMIN)) {
            log.debug("An administrator already exists - INITIAL_ADMIN_* variables are ignored");
            return;
        }

        userRepository.findByEmail(email).ifPresentOrElse(this::promote, this::create);
    }

    private void promote(User user) {
        user.setRole(RoleEnum.ADMIN);
        // An administrator must be able to log in right away
        user.setEmailVerified(true);
        user.setBannedUntil(null);
        user.setBanReason(null);
        userRepository.save(user);
        log.info("Existing account {} has been given the ADMIN role (password unchanged)", email);
    }

    private void create() {
        // A misconfigured deployment should fail loudly instead of creating an administrator with a weak password
        if (password.length() < MIN_PASSWORD_LENGTH) {
            throw new IllegalStateException("INITIAL_ADMIN_PASSWORD must have at least "
                    + MIN_PASSWORD_LENGTH + " characters to create the administrator account " + email);
        }
        User admin = User.builder()
                .email(email)
                .passwordHash(passwordEncoder.encode(password))
                .displayName("Administrator")
                .mobile(PLACEHOLDER_MOBILE)
                .role(RoleEnum.ADMIN)
                .userType(UserTypeEnum.PRIVATE)
                .isEmailVerified(true)
                .build();
        userRepository.save(admin);
        log.info("Administrator account {} has been created", email);
    }
}

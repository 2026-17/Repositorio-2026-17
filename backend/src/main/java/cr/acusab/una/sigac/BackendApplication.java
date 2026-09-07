package cr.acusab.una.sigac;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

@SpringBootApplication
public class BackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(BackendApplication.class, args);

        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        String raw = "Admin123!";
        String hash = encoder.encode(raw);

        System.out.println("Password: " + raw);
        System.out.println("BCrypt hash: " + hash);
        System.out.println("Match? " + encoder.matches(raw, hash));

    }



}

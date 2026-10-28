package com.example.demo.Config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Envío de correos en segundo plano.
 *
 * Así la respuesta HTTP de "solicitar recuperación" no depende de la latencia
 * del proveedor SMTP y el mensaje genérico tarda lo mismo exista o no la
 * cuenta. El executor es pequeño y con cola acotada para evitar agotar hilos.
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    public static final String PASSWORD_RESET_MAIL_EXECUTOR = "passwordResetMailExecutor";

    @Bean(name = PASSWORD_RESET_MAIL_EXECUTOR)
    public Executor passwordResetMailExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("password-reset-mail-");
        executor.setWaitForTasksToCompleteOnShutdown(false);
        executor.initialize();
        return executor;
    }
}
